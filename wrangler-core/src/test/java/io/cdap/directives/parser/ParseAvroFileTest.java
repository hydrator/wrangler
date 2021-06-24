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

import com.google.common.base.Charsets;
import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.RecipeException;
import io.cdap.wrangler.api.Row;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests {@link ParseAvroFile}
 */
public class ParseAvroFileTest {

  @Test
  public void testParseAsAvroFile() throws Exception {
    InputStream stream = ParseAvroFileTest.class.getClassLoader().getResourceAsStream("cdap-log.avro");
    byte[] data = IOUtils.toByteArray(stream);

    String[] directives = new String[] {
      "parse-as-avro-file body",
    };

    List<Row> rows = new ArrayList<>();
    rows.add(new Row("body", data));

    List<Row> results = TestingRig.execute(directives, rows);
    Assert.assertEquals(1689, results.size());
    Assert.assertEquals(15, results.get(0).width());
    Assert.assertEquals(1495172588118L, results.get(0).getValue("timestamp"));
    Assert.assertEquals(1495194308245L, results.get(1688).getValue("timestamp"));
  }

  @Test
  public void testAvroWithJsonFieldParsingWithParseJsonIgnoreError() throws Exception {
    InputStream stream = ParseAvroFileTest.class.getClassLoader().getResourceAsStream("bad.avro");
    byte[] data = IOUtils.toByteArray(stream);

    String[] directives = new String[] {
      "parse-as-avro-file body",
      "parse-as-json :message_contents 2 true"
    };

    List<Row> rows = new ArrayList<>();
    rows.add(new Row("body", data));

    List<Row> results = TestingRig.execute(directives, rows);
    Assert.assertEquals(6693, results.size()); // total 6670, 7 records are bad.
  }

  @Test
  public void testAvroWithJsonFieldParsingWithParseJsonIgnoreErrorFail() throws Exception {
    InputStream stream = ParseAvroFileTest.class.getClassLoader().getResourceAsStream("bad.avro");
    byte[] data = IOUtils.toByteArray(stream);

    String[] directives = new String[] {
      "parse-as-avro-file body",
      "parse-as-json :message_contents 2"
    };

    List<Row> rows = new ArrayList<>();
    rows.add(new Row("body", data));

    List<Row> results = TestingRig.execute(directives, rows);
    Assert.assertEquals(0, results.size()); // total 6670, 7 records are bad.
  }

  @Test(expected = RecipeException.class)
  public void testIncorrectType() throws Exception {
    String[] directives = new String[] {
      "parse-as-avro-file body",
    };

    List<Row> rows = new ArrayList<>();
    rows.add(new Row("body", new String("failure").getBytes(Charsets.UTF_8)));
    TestingRig.execute(directives, rows);
  }

}
