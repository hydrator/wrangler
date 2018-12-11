/*
 * Copyright © 2018 Cask Data, Inc.
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

package co.cask.wrangler.proto.workspace;

import com.google.gson.annotations.SerializedName;

/**
 * Information about a workspace
 */
public class WorkspaceInfo {
  private final String id;
  private final String name;
  private final String delimiter;
  private final String charset;
  @SerializedName("Content-Type")
  private final String contentType;
  // this is the connection type
  private final String connection;
  private final String sampler;

  public WorkspaceInfo(String id, String name, String delimiter, String charset, String contentType,
                       String connection, String sampler) {
    this.id = id;
    this.name = name;
    this.delimiter = delimiter;
    this.charset = charset;
    this.contentType = contentType;
    this.connection = connection;
    this.sampler = sampler;
  }
}
