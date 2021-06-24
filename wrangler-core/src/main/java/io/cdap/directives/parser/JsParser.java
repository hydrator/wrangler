/*
 *  Copyright © 2017-2020 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.directives.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.internal.LazilyParsedNumber;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ErrorRowException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Optional;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Many;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.Bool;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Numeric;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.dq.TypeInference;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class is a JSON Parser directive with optional argument specifying the depth
 * to which the JSON needs to be parsed.
 */
@Plugin(type = Directive.TYPE)
@Name("parse-as-json")
@Categories(categories = { "parser", "json"})
@Description("Parses a column as JSON.")
public class JsParser implements Directive, Lineage {
  public static final String NAME = "parse-as-json";
  // Column within the input row that needs to be parsed as Json
  private String column;

  // Max depth to which the JSON needs to be parsed.
  private int depth;

  // Ignore error in parsing for record and proceed.
  private boolean ignoreError;

  // Gson parser with adapter to ensure integers are not represented as doubles.
  private static final Gson parser = new GsonBuilder()
    .setLenient()
    .registerTypeAdapter(Double.class, new JsonSerializer<Double>() {
      @Override
      public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {
        if (src == src.longValue()) {
          return new JsonPrimitive(src.longValue());
        }
        return new JsonPrimitive(src.doubleValue());
      }
    })
    .create();

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("column", TokenType.COLUMN_NAME);
    builder.define("depth", TokenType.NUMERIC, Optional.TRUE);
    builder.define("ignore-error", TokenType.BOOLEAN, Optional.TRUE);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    this.column = ((ColumnName) args.value("column")).value();
    if (args.contains("depth")) {
      this.depth = ((Numeric) args.value("depth")).value().intValue();
    } else {
      this.depth = Integer.MAX_VALUE;
    }
    if (args.contains("ignore-error")) {
      this.ignoreError = ((Bool) args.value("ignore-error")).value();
    } else {
      this.ignoreError = false; // backward compaibility.
    }
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context)
    throws DirectiveExecutionException, ErrorRowException {
    List<Row> results = new ArrayList<>();

    // Iterate through all the rows.
    for (Row row : rows) {
      int idx = row.find(column);
      // If the input column exists in the row, proceed further.
      if (idx != -1) {
        Object value = row.getValue(idx);
        if (value == null) {
          continue;
        }

        try {
          JsonElement element = null;
          if (value instanceof String) {
            String document = (String) value;
            element = parser.fromJson(document.trim(), JsonElement.class);
          } else if (value instanceof JsonObject || value instanceof JsonArray) {
            element = (JsonElement) value;
          } else {
            throw new DirectiveExecutionException(
              NAME, String.format("Column '%s' is of invalid type '%s'. It should be of type 'String'" +
                                    " or 'JsonObject' or 'JsonArray'.", column, value.getClass().getSimpleName()));
          }

          row.remove(idx);

          if (element != null) {
            if (element instanceof JsonObject) {
              jsonFlatten(element.getAsJsonObject(), column, 1, depth, row);
              results.add(row);
            } else if (element instanceof JsonArray) {
              JsonArray array = element.getAsJsonArray();
              if (array.size() > 0) {
                for (int i = 0; i < array.size(); ++i) {
                  JsonElement object = array.get(i);
                  Row newRow = new Row(row);
                  newRow.add(column, getValue(object));
                  results.add(newRow);
                }
              } else {
                results.add(row);
              }
            } else if (element instanceof JsonPrimitive) {
              row.add(column, getValue(element.getAsJsonPrimitive()));
            }
          }
        } catch (Exception e) {
          String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
          // In case there are more rows being handled, we attempt to surface the Json that is
          // causing an issue to make it easier for users to identify the problem quickly.
          if (rows.size() > 1) {
            msg = String.format("Incorrectly constructed json '%s', %s", value, e.getCause() != null ?
              e.getCause().getMessage() : e.getMessage());
          }

          // If ignore error set, then don't throw exception, just move to next record.
          if (!ignoreError) {
            throw new ErrorRowException(NAME, msg, 1);
          }
        }
      }
    }
    return results;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Parsed column '%s' as Json", column)
      .all(Many.columns(column))
      .build();
  }

  /**
   * Recursively flattens JSON until the 'depth' is reached.
   *
   * @param root of the JSONObject
   * @param field name to be used to be stored in the row.
   * @param depth current depth into JSON structure.
   * @param maxDepth maximum depth to reach
   * @param row to which the flatten fields need to be added.
   */
  public static void jsonFlatten(JsonObject root, String field, int depth, int maxDepth, Row row) {
    if (depth > maxDepth) {
      row.addOrSet(String.format("%s", field), root);
      return;
    }

    Iterator<Map.Entry<String, JsonElement>> elements = root.entrySet().iterator();
    while (elements.hasNext()) {
      Map.Entry<String, JsonElement> next = elements.next();
      String key = next.getKey();
      JsonElement element = next.getValue();
      if (element instanceof JsonObject) {
        jsonFlatten(element.getAsJsonObject(),
                    String.format("%s_%s", field, key), depth + 1, maxDepth, row);
      } else {
        row.add(String.format("%s_%s", field, key), getValue(element));
      }
    }
  }

  /**
   * Gets a single value from the {@link JsonElement}. The value could be
   * {@link JsonObject} or {@link JsonArray} or {@link JsonPrimitive}.
   *
   * @param element value to be extracted.
   * @return the sub-element, else {@link com.google.gson.JsonNull}.
   */
  public static Object getValue(JsonElement element) {
    if (element.isJsonObject()) {
      return element.getAsJsonObject();
    } else if (element.isJsonArray()) {
      return element.getAsJsonArray();
    } else if (element.isJsonPrimitive()) {
      return getValue(element.getAsJsonPrimitive());
    }
    return element.getAsJsonNull();
  }

  /**
   * Extracts a value from the {@link JsonPrimitive}.
   *
   * @param primitive object to extract real value.
   * @return java type extracted from the {@link JsonPrimitive}
   */
  public static Object getValue(JsonPrimitive primitive) {
    if (primitive.isBoolean()) {
      return primitive.getAsBoolean();
    } else if (primitive.isNumber()) {
      Number number = primitive.getAsNumber();
      if (number instanceof Long) {
        return number.longValue();
      } else if (number instanceof Double) {
        return number.doubleValue();
      } else if (number instanceof Integer) {
        return number.intValue();
      } else if (number instanceof Short) {
        return number.shortValue();
      } else if (number instanceof Float) {
        return number.floatValue();
      } else if (number instanceof BigInteger) {
        return primitive.getAsBigInteger().longValue();
      } else if (number instanceof BigDecimal) {
        return primitive.getAsBigDecimal().doubleValue();
      } else if (number instanceof LazilyParsedNumber) {
        if (TypeInference.isInteger(primitive.getAsString())) {
          return primitive.getAsBigInteger().longValue();
        } else {
          return primitive.getAsBigDecimal().doubleValue();
        }
      }
    } else if (primitive.isString()) {
      return primitive.getAsString();
    } else if (primitive.isJsonNull()) {
      return primitive.getAsJsonNull();
    }
    return null;
  }
}
