/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.wrangler.api;

import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.schema.Schema;
import co.cask.cdap.format.StructuredRecordStringConverter;
import co.cask.wrangler.internal.PipelineExecutor;
import co.cask.wrangler.internal.TextDirectives;
import co.cask.wrangler.utils.Json2Schema;
import co.cask.wrangler.utils.JsonPathGenerator;
import co.cask.wrangler.utils.RecordConvertor;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests {@link Json2Schema}
 */
public class Json2SchemaTest {
  private static final String BASIC = "{\n" +
    "  \"a\" : 1,\n" +
    "  \"b\" : 2.0,\n" +
    "  \"c\" : \"test\",\n" +
    "  \"d\" : true\n" +
    "}";
  private static final String ARRAY_OF_OBJECTS = "[\n" +
    "  { \"a\" : 1, \"b\" : 2, \"c\" : \"x\" },\n" +
    "  { \"a\" : 2, \"b\" : 3, \"c\" : \"y\" },\n" +
    "  { \"a\" : 3, \"b\" : 4, \"c\" : \"z\" }\n" +
    "]";
  private static final String SIMPLE_JSON_OBJECT = "{\n" +
    "  \"fname\" : \"root\",\n" +
    "  \"lname\" : \"joltie\",\n" +
    "  \"age\" : 20,\n" +
    "  \"weight\" : 182.3,\n" +
    "  \"location\" : \"New York\",\n" +
    "  \"address\" : {\n" +
    "    \"city\" : \"New York\",\n" +
    "    \"state\" : \"New York\",\n" +
    "    \"zip\" : 97474,\n" +
    "    \"gps\" : {\n" +
    "      \"lat\" : 12.23,\n" +
    "      \"long\" : 14.54,\n" +
    "      \"universe\" : {\n" +
    "        \"galaxy\" : \"milky way\",\n" +
    "        \"start\" : \"sun\",\n" +
    "        \"size\" : 24000,\n" +
    "        \"alive\" : true\n" +
    "      }\n" +
    "    }\n" +
    "  }\n" +
    "}";
  private static final String JSON_ARRAY_WITH_OBJECT = "[\n" +
    " {\n" +
    "  \"fname\" : \"root\",\n" +
    "  \"lname\" : \"joltie\",\n" +
    "  \"age\" : 20,\n" +
    "  \"weight\" : 182.3,\n" +
    "  \"location\" : \"New York\",\n" +
    "  \"address\" : {\n" +
    "    \"city\" : \"New York\",\n" +
    "    \"state\" : \"New York\",\n" +
    "    \"zip\" : 97474,\n" +
    "    \"gps\" : {\n" +
    "      \"lat\" : 12.23,\n" +
    "      \"long\" : 14.54,\n" +
    "      \"universe\" : {\n" +
    "        \"galaxy\" : \"milky way\",\n" +
    "        \"start\" : \"sun\",\n" +
    "        \"size\" : 24000,\n" +
    "        \"alive\" : true,\n" +
    "        \"population\" : [ 4,5,6,7,8,9]\n" +
    "      }\n" +
    "    }\n" +
    "  }\n" +
    "}\n" +
    "]";
  private static final String COMPLEX_1 = "{\n" +
    "  \"numbers\" : [ 1,2,3,4,5,6],\n" +
    "  \"object\" : {\n" +
    "    \"a\" : 1,\n" +
    "    \"b\" : 2,\n" +
    "    \"c\" : [ \"a\", \"b\", \"c\", \"d\" ],\n" +
    "    \"d\" : [ \n" +
    "      { \"a\" : 1 },\n" +
    "      { \"a\" : 2 },\n" +
    "      { \"a\" : 3 }\n" +
    "    ]\n" +
    "  }\n" +
    "}";
  private static final String ARRAY_OF_NUMBERS = "[ 1, 2, 3, 4, 5]";
  private static final String ARRAY_OF_STRING = "[ \"A\", \"B\", \"C\"]";
  private static final String COMPLEX_2 = "{\n" +
    "  \"a\" : [ 1, 2, 3, 4],\n" +
    "  \"b\" : [ \"A\", \"B\", \"C\"],\n" +
    "  \"d\" : true,\n" +
    "  \"e\" : 1,\n" +
    "  \"f\" : \"string\",\n" +
    "  \"g\" : {\n" +
    "    \"g1\" : [ 1, 2, 3, 4],\n" +
    "    \"g2\" : [\n" +
    "      { \"g21\" : 1}\n" +
    "    ]\n" +
    "  }\n" +
    "}";
  private static final String EMPTY_OBJECT = "{ \"dividesplitdetails\":{\"type0\":[]}}";

  private static final String[] TESTS = new String[] {
    BASIC,
    SIMPLE_JSON_OBJECT,
    ARRAY_OF_OBJECTS,
    JSON_ARRAY_WITH_OBJECT,
    COMPLEX_1,
    ARRAY_OF_NUMBERS,
    ARRAY_OF_STRING,
    COMPLEX_2,
    EMPTY_OBJECT
  };

  private static final String[] directives = new String[] {
    "set-column body json:select(body, \"$\")"
  };

  @Test
  public void conversionTest() throws Exception {
    Json2Schema converter = new Json2Schema();
    RecordConvertor recordConvertor = new RecordConvertor();
    PipelineExecutor executor = new PipelineExecutor();
    JsonParser parser = new JsonParser();
    executor.configure(new TextDirectives(directives), null);
    for (String test : TESTS) {
      Record record = new Record("body", test);

      List<Record> records = executor.execute(Lists.newArrayList(record));
      Schema schema = converter.toSchema("myrecord", records.get(0));
      if (schema.getType() != Schema.Type.RECORD) {
        schema = Schema.recordOf("array", Schema.Field.of("array", schema));
      }
      Assert.assertNotNull(schema);
      List<StructuredRecord> structuredRecords = recordConvertor.toStructureRecord(records, schema);
      String decode = StructuredRecordStringConverter.toJsonString(structuredRecords.get(0));
      JsonElement originalObject = parser.parse(test);
      JsonElement roundTripObject = parser.parse(decode).getAsJsonObject().get("body");
      Assert.assertTrue(originalObject.equals(roundTripObject));
      Assert.assertTrue(structuredRecords.size() > 0);
    }
  }

  @Test
  public void testJsonPathGeneration() throws Exception {
    JsonPathGenerator paths = new JsonPathGenerator();
    List<String> path = paths.get(COMPLEX_1);
    Assert.assertEquals(path.size(), 23);
    String s = JsonNull.INSTANCE.toString();
  }
}