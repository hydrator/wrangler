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

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link FillNullOrEmpty}
 */
public class FillNullOrEmptyTest {

  @Test
  public void testColumnNotPresent() throws Exception {
    String[] directives = new String[] {
      "fill-null-or-empty null N/A",
    };

    List<Row> rows = Arrays.asList(
      new Row("value", "has value"),
      new Row("value", null),
      new Row("value", null)
    );

    TestingRig.execute(directives, rows);
    Assert.assertTrue(rows.size() == 3);
  }

  @Test
  public void testBasicNullCase() throws Exception {
    String[] directives = new String[] {
      "fill-null-or-empty value N/A",
    };

    List<Row> rows = Arrays.asList(
      new Row("value", "has value"),
      new Row("value", null),
      new Row("value", null)
    );

    rows = TestingRig.execute(directives, rows);

    Assert.assertTrue(rows.size() == 3);
    Assert.assertEquals("has value", rows.get(0).getValue("value"));
    Assert.assertEquals("N/A", rows.get(1).getValue("value"));
    Assert.assertEquals("N/A", rows.get(2).getValue("value"));
  }

  @Test
  public void testEmptyStringCase() throws Exception {
    String[] directives = new String[] {
      "fill-null-or-empty value N/A",
    };

    List<Row> rows = Arrays.asList(
      new Row("value", ""),
      new Row("value", ""),
      new Row("value", "Should be fine")
    );

    rows = TestingRig.execute(directives, rows);

    Assert.assertTrue(rows.size() == 3);
    Assert.assertEquals("N/A", rows.get(0).getValue("value"));
    Assert.assertEquals("N/A", rows.get(1).getValue("value"));
    Assert.assertEquals("Should be fine", rows.get(2).getValue("value"));
  }

  @Test
  public void testMixedCases() throws Exception {
    String[] directives = new String[] {
      "fill-null-or-empty value N/A",
    };

    List<Row> rows = Arrays.asList(
      new Row("value", null),
      new Row("value", ""),
      new Row("value", "Should be fine")
    );

    rows = TestingRig.execute(directives, rows);

    Assert.assertTrue(rows.size() == 3);
    Assert.assertEquals("N/A", rows.get(0).getValue("value"));
    Assert.assertEquals("N/A", rows.get(1).getValue("value"));
    Assert.assertEquals("Should be fine", rows.get(2).getValue("value"));
  }

  @Test
  public void testSpace() throws Exception {
    String[] directives = new String[] {
      "fill-null-or-empty :value 'Not Available'",
    };

    List<Row> rows = Arrays.asList(
      new Row("value", null),
      new Row("value", null),
      new Row("value", "Should be fine")
    );

    rows = TestingRig.execute(directives, rows);

    Assert.assertTrue(rows.size() == 3);
    Assert.assertEquals("Not Available", rows.get(0).getValue("value"));
    Assert.assertEquals("Not Available", rows.get(1).getValue("value"));
    Assert.assertEquals("Should be fine", rows.get(2).getValue("value"));
  }

  @Test
  public void testGsonNull() throws Exception {
    String[] directives = new String[]{
      "parse-as-json :body",
      "fill-null-or-empty :body_value 'Not Available'"
    };

    List<Row> rows = Arrays.asList(
      new Row("body", "{\"value\": null }")
    );

    rows = TestingRig.execute(directives, rows);

    Assert.assertTrue(rows.size() == 1);
    Assert.assertEquals("Not Available", rows.get(0).getValue("body_value"));
  }
}
