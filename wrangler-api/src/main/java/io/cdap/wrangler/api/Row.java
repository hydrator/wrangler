/*
 * Copyright © 2016-2019 Cask Data, Inc.
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

package io.cdap.wrangler.api;

import io.cdap.wrangler.api.annotations.PublicEvolving;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Row defines the schema and data on which the wrangler will operate upon.
 */
@PublicEvolving
public final class Row implements Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(Row.class);

  // Name of the columns held by the row.
  private List<String> columns = new ArrayList<>();

  // Values held by the row.
  private List<Object> values = new ArrayList<>();

  public Row() {
  }

  /**
   * Makes a copy of the row.
   *
   * @param row to be copied to 'this' object.
   */
  public Row(Row row) {
    this.values = new ArrayList<>(row.values);
    this.columns = new ArrayList<>(row.columns);
  }

  /**
   * Initializes a row with list of columns.
   *
   * @param columns to set in the row.
   */
  public Row(List<String> columns) {
    this.columns = new ArrayList<>(columns);
    this.values = new ArrayList<>(columns.size());
  }

  /**
   * Initializes the row with column name and value.
   *
   * @param name of the column to be added to the row.
   * @param value for the column defined above.
   */
  public Row(String name, Object value) {
    this.columns = new ArrayList<>(1);
    this.values = new ArrayList<>(1);
    this.columns.add(name);
    this.values.add(value);
  }

  /**
   * Gets a column name by index.
   *
   * @param idx to retrieve the name of the column.
   * @return name of the column.
   */
  public String getColumn(int idx) {
    return columns.get(idx);
  }

  /**
   * Sets the name of the column at a given index.
   *
   * @param idx at which the new name to be set.
   * @param name of the column to be set at idx.
   */
  public void setColumn(int idx, String name) {
    columns.set(idx, name);
  }

  /**
   * Gets a value of row at specified index.
   *
   * @param idx from where the value should be retrieved.
   * @return value at index (idx).
   */
  public Object getValue(int idx) {
    return values.get(idx);
  }

  /**
   * Gets value based on the column name.
   *
   * @param col name of the column for which the value is retrieved.
   * @return value associated with column.
   */
  public Object getValue(String col) {
    if (col != null && !col.isEmpty()) {
      int idx = find(col);
      if (idx != -1) {
        return values.get(idx);
      }
    }
    return null;
  }

  /**
   * Updates the value of the row at index idx.
   *
   * @param idx index at which the value needs to be updated.
   * @param value value to be updated at index (idx).
   */
  public Row setValue(int idx, Object value) {
    values.set(idx, value);
    return this;
  }

  /**
   * Adds a value into row with name.
   *
   * @param name of the value to be added to row.
   * @param value to be added to row.
   */
  public Row add(String name, Object value) {
    columns.add(name);
    values.add(value);
    return this;
  }

  /**
   * Removes the column and value at given index.
   *
   * @param idx for which the value and column are removed.
   */
  public Row remove(int idx) {
    columns.remove(idx);
    values.remove(idx);
    return this;
  }

  /**
   * Finds a column index based on the name of the column. The col name is case insensitive.
   *
   * @param col to be searched within the row.
   * @return -1 if not present, else the index at which the column is found.
   */
  public int find(String col) {
    int idx = 0;
    for (String name : columns) {
      if (col.equalsIgnoreCase(name)) {
        return idx;
      }
      idx++;
    }
    return -1;
  }

  /**
   * @return  width of the row.
   */
  @Deprecated
  public int length() {
    return columns.size();
  }

  /**
   * @return  width of the row.
   */
  public int width() {
    return columns.size();
  }

  /**
   * @return List of fields of record.
   */
  public List<Pair<String, Object>> getFields() {
    List<Pair<String, Object>> v = new ArrayList<>();
    int i = 0;
    for (String column : columns) {
      v.add(new Pair<>(column, values.get(i)));
      ++i;
    }
    return v;
  }

  /**
   * Adds or sets the value.
   *
   * @param name of the field to be either set or added to record.
   * @param value to be added.
   */
  public void addOrSet(String name, Object value) {
    int idx = find(name);
    if (idx != -1) {
      setValue(idx, value);
    } else {
      add(name, value);
    }
  }

  /**
   * Adds or sets the value to the beginning.
   *
   * @param index at which the column need to be inserted.
   * @param name of the field to be either set or added to record.
   * @param value to be added.
   */
  public void addOrSetAtIndex(int index, String name, Object value) {
    int idx = find(name);
    if (idx != -1) {
      setValue(idx, value);
    } else {
      if (index < columns.size() && index < values.size()) {
        columns.add(index, name);
        values.add(index, value);
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Row row = (Row) o;
    return Objects.equals(columns, row.columns) &&
             Objects.equals(values, row.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(columns, values);
  }
}
