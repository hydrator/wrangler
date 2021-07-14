/*
 * Copyright © 2021 Cask Data, Inc.
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
 *
 */

package io.cdap.wrangler.service.directive;

import com.google.common.base.Stopwatch;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.cdap.cdap.api.ServiceDiscoverer;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.api.retry.RetryableException;
import io.cdap.cdap.etl.api.connector.SampleRequest;
import io.cdap.cdap.etl.common.Constants;
import io.cdap.cdap.etl.proto.connection.ConnectionCreationRequest;
import io.cdap.cdap.etl.proto.connection.ConnectorDetail;
import io.cdap.cdap.etl.proto.connection.SampleResponse;
import io.cdap.cdap.etl.proto.connection.SampleResponseCodec;
import io.cdap.cdap.etl.proto.connection.SpecGenerationRequest;
import io.cdap.cdap.internal.io.SchemaTypeAdapter;
import io.cdap.cdap.proto.id.NamespaceId;
import io.cdap.wrangler.proto.BadRequestException;
import io.cdap.wrangler.proto.ConflictException;
import io.cdap.wrangler.proto.NotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Discover for connection related operations
 */
public class ConnectionDiscoverer {
  private static final Gson GSON =
    new GsonBuilder()
      .registerTypeAdapter(Schema.class, new SchemaTypeAdapter())
      .registerTypeAdapter(SampleResponse.class, new SampleResponseCodec()).create();

  private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
  private static final long RETRY_BASE_DELAY_MILLIS = 200L;
  private static final long RETRY_MAX_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(5);
  private static final double RETRY_DELAY_MULTIPLIER = 1.2d;
  private static final double RETRY_RANDOMIZE_FACTOR = 0.1d;

  private final ServiceDiscoverer serviceDiscoverer;

  public ConnectionDiscoverer(ServiceDiscoverer serviceDiscoverer) {
    this.serviceDiscoverer = serviceDiscoverer;
  }

  public void addConnection(String namespace, String connectionName,
                            ConnectionCreationRequest request) throws IOException, InterruptedException {
    String url = String.format("v1/contexts/%s/connections/%s", namespace, connectionName);
    execute(namespace, connectionName, url, urlConn -> {
      urlConn.setRequestMethod("PUT");
      urlConn.setDoOutput(true);
      try (OutputStream os = urlConn.getOutputStream();
           OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
        writer.write(GSON.toJson(request));
        writer.flush();
      }
    }, null);
  }

  public ConnectorDetail getSpecification(String namespace, String connectionName,
                                          SpecGenerationRequest request) throws IOException, InterruptedException {
    String url = String.format("v1/contexts/%s/connections/%s/specification", namespace, connectionName);
    return execute(namespace, connectionName, url, urlConn -> {
      urlConn.setRequestMethod("POST");
      urlConn.setDoOutput(true);
      try (OutputStream os = urlConn.getOutputStream();
           OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
        writer.write(GSON.toJson(request));
        writer.flush();
      }
    }, ConnectorDetail.class);
  }

  public SampleResponse retrieveSample(String namespace, String connectionName,
                                       SampleRequest sampleRequest) throws IOException, InterruptedException {
    String url = String.format("v1/contexts/%s/connections/%s/sample", namespace, connectionName);
    return execute(namespace, connectionName, url, urlConn -> {
      urlConn.setRequestMethod("POST");
      urlConn.setDoOutput(true);
      try (OutputStream os = urlConn.getOutputStream();
           OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
        writer.write(GSON.toJson(sampleRequest));
        writer.flush();
      }
    }, SampleResponse.class);
  }

  /**
   * Execute the url provided, and return the response based on given type.
   */
  private <T> T execute(String namespace, String connectionName, String url,
                        URLConfigurer configurer, @Nullable Class<T> type) throws IOException, InterruptedException {
    // Make call with exponential delay on failure retry.
    long delay = RETRY_BASE_DELAY_MILLIS;
    double minMultiplier = RETRY_DELAY_MULTIPLIER - RETRY_DELAY_MULTIPLIER * RETRY_RANDOMIZE_FACTOR;
    double maxMultiplier = RETRY_DELAY_MULTIPLIER + RETRY_DELAY_MULTIPLIER * RETRY_RANDOMIZE_FACTOR;
    Stopwatch stopWatch = Stopwatch.createStarted();

    // remember the latest retryable exception;
    RetryableException latest = null;
    while (stopWatch.elapsed(TimeUnit.MILLISECONDS) < TIMEOUT_MILLIS) {
      try {
        HttpURLConnection urlConn = retrieveConnectionUrl(url);
        configurer.configure(urlConn);
        return retrieveResult(urlConn, type);
      } catch (RetryableException e) {
        latest = e;
        TimeUnit.MILLISECONDS.sleep(delay);
        delay = (long) (delay * (minMultiplier + Math.random() * (maxMultiplier - minMultiplier + 1)));
        delay = Math.min(delay, RETRY_MAX_DELAY_MILLIS);
      } catch (IOException e) {
        throw new IOException(
          String.format("Failed to retrieve sample for connection '%s' in namespace '%s'. Error: %s",
                        connectionName, namespace, e.getMessage()), e);
      }
    }

    if (latest != null) {
      throw new IOException(String.format("Timed out when trying to retrieve sample for " +
                                            "connection '%s' in namespace '%s'.", connectionName, namespace), latest);
    }

    // should not get here, the call should either already fail with a non retryable failure or timed out because of
    // retryable failure
    throw new IllegalStateException(String.format("Unable to retrieve sample for " +
                                                    "connection '%s' in namespace '%s'.", connectionName, namespace));
  }

  private HttpURLConnection retrieveConnectionUrl(String url) throws IOException {
    HttpURLConnection urlConn = serviceDiscoverer.openConnection(
      NamespaceId.SYSTEM.getNamespace(), Constants.PIPELINEID, Constants.STUDIO_SERVICE_NAME, url);

    if (urlConn == null) {
      throw new RetryableException("Connection service is not available");
    }
    return urlConn;
  }

  /**
   * Retrieve the result from the url conn.
   *
   * @param urlConn url connection to get result
   * @param type the expected return type for this call
   */
  private <T> T retrieveResult(HttpURLConnection urlConn, @Nullable Class<T> type) throws IOException {
    int responseCode = urlConn.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) {
      switch (responseCode) {
        case HttpURLConnection.HTTP_BAD_GATEWAY:
        case HttpURLConnection.HTTP_UNAVAILABLE:
        case HttpURLConnection.HTTP_GATEWAY_TIMEOUT:
          throw new RetryableException("Connection service is not available with status " + responseCode);
        case HttpURLConnection.HTTP_BAD_REQUEST:
          throw new BadRequestException(getError(urlConn));
        case HttpURLConnection.HTTP_NOT_FOUND:
          throw new NotFoundException(getError(urlConn));
        case HttpURLConnection.HTTP_CONFLICT:
          throw new ConflictException(getError(urlConn));
      }
      throw new IOException("Failed to call connection service with status " + responseCode + ": " +
                              getError(urlConn));
    }

    if (type == null) {
      urlConn.disconnect();
      return null;
    }
    try (Reader reader = new InputStreamReader(urlConn.getInputStream(), StandardCharsets.UTF_8)) {
      return GSON.fromJson(CharStreams.toString(reader), type);
    } finally {
      urlConn.disconnect();
    }
  }

  /**
   * Returns the full content of the error stream for the given {@link HttpURLConnection}.
   */
  private String getError(HttpURLConnection urlConn) {
    try (InputStream is = urlConn.getErrorStream()) {
      if (is == null) {
        return "Unknown error";
      }
      return new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "Unknown error due to failure to read from error output: " + e.getMessage();
    }
  }

  /**
   * Interface for the methods to configure the url conn
   */
  private interface URLConfigurer {
    void configure(HttpURLConnection urlConn) throws IOException;
  }
}
