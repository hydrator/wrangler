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
import io.cdap.wrangler.api.lineage.Many;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A step to parse AVRO File.
 */
@Plugin(type = Directive.TYPE)
@Name("parse-as-avro-file")
@Categories(categories = { "parser", "avro"})
@Description("parse-as-avro-file <column>.")
public class ParseAvroFile implements Directive, Lineage {
  public static final String NAME = "parse-as-avro-file";
  private static final int AVRO_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
  private String column;
  private Gson gson;

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("column", TokenType.COLUMN_NAME);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    this.column = ((ColumnName) args.value("column")).value();
    gson = new Gson();
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public List<Row> execute(List<Row> rows, final ExecutorContext context) throws DirectiveExecutionException {
    List<Row> results = new ArrayList<>();
    for (Row row : rows) {
      int idx = row.find(column);
      if (idx != -1) {
        Object object = row.getValue(idx);
        if (object instanceof byte[]) {
          int size = ((byte[]) object).length;
          if (size > AVRO_FILE_SIZE_LIMIT) {
            throw new DirectiveExecutionException(
              NAME,
              String.format("Avro file greater than 5 MB are not currently supported by this directive " +
                              "(Current size : %d). Use File source connector with 'avro' as format to " +
                              "read large files.", size)
            );
          }
          DataFileReader<GenericRecord> reader = null;
          try {
            reader =
              new DataFileReader<>(new SeekableByteArrayInput((byte[]) object), new GenericDatumReader<>());
            while (reader.hasNext()) {
              Row newRow = new Row();
              add(reader.next(), newRow, null);
              results.add(newRow);
            }
          } catch (IOException e) {
            throw new DirectiveExecutionException(NAME, "Failed to parse Avro data file. " + e.getMessage(), e);
          } finally {
            if (reader != null) {
              try {
                reader.close();
              } catch (IOException e) {
                // Nothing can be done.
              }
            }
          }
        } else {
          throw new DirectiveExecutionException(
            NAME, String.format("Avro data file parsing directive requires '%s' to be a bytes field. " +
                                  "Change type in input to bytes and make sure the format in File source is " +
                                  "set as 'blob'. We recommend using Avro format in file source instead of using " +
                                  "this directive",
                                column));
        }
      }
    }
    return results;
  }

  @Override
  public Mutation lineage() {
    return Mutation.builder()
      .readable("Parsed column '%s' as a Avro file", column)
      .all(Many.columns(column))
      .build();
  }

  /**
   * Flattens the {@link GenericRecord}.
   *
   * @param genericRecord to be flattened.
   * @param row to be flattened into
   * @param name of the field to be flattened.
   */
  private void add(GenericRecord genericRecord, Row row, String name) {
    List<Schema.Field> fields = genericRecord.getSchema().getFields();
    String colname;
    for (Schema.Field field : fields) {
      Object v = genericRecord.get(field.name());
      if (name != null) {
        colname = String.format("%s_%s", name, field.name());
      } else {
        colname = field.name();
      }
      if (v instanceof GenericRecord) {
        add((GenericRecord) v, row, colname);
      } else if (v instanceof Map || v instanceof List) {
        row.add(colname, gson.toJson(v));
      } else if (v instanceof Utf8) {
        row.add(colname, v.toString());
      } else {
        row.add(colname, genericRecord.get(field.name()));
      }
    }
  }
}
