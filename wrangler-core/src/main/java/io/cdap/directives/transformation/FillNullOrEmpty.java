/*
 *  Copyright © 2017-2019 Cask Data, Inc.
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

package io.cdap.directives.transformation;

import com.google.gson.JsonNull;
import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import org.json.JSONObject;

import java.util.List;

/**
 * A directive to fill null or empty column values with a fixed value.
 */
@Plugin(type = Directive.TYPE)
@Name(FillNullOrEmpty.NAME)
@Categories(categories = { "transform"})
@Description("Fills a value of a column with a fixed value if it is either null or empty.")
public class FillNullOrEmpty implements Directive, Lineage {
  public static final String NAME = "fill-null-or-empty";
  private String column;
  private String value;

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("column", TokenType.COLUMN_NAME);
    builder.define("value", TokenType.TEXT);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    this.column = ((ColumnName) args.value("column")).value();
    this.value = ((Text) args.value("value")).value();
    if (value != null && value.isEmpty()) {
      throw new DirectiveParseException(NAME, "Fixed value cannot be an empty string");
    }
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context)
    throws DirectiveExecutionException {
    for (Row row : rows) {
      int idx = row.find(column);
      if (idx == -1) {
        row.add(column, value);
        continue;
      }
      Object object = row.getValue(idx);
      if (object == null) {
        row.setValue(idx, value);
      } else {
        if (object instanceof String) {
          if (((String) object).isEmpty()) {
            row.setValue(idx, value);
          }
        } else if (object instanceof JSONObject) {
          if (JSONObject.NULL.equals(object)) {
            row.setValue(idx, value);
          }
        } else if (object instanceof JsonNull) {
          row.setValue(idx, value);
        }
      }
    }
    return rows;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Filled column '%s' values that were null or empty with value %s", column, value)
      .relation(column, column)
      .build();
  }
}
