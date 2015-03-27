/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.testing;

import com.google.common.base.Throwables;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.http.HttpHost;

/** Wraps an OkHttpClient and sends all requests to one host. */
class HostBoundWrappedHttpClient extends OkHttpClient {
  private final HttpHost boundHost;
  private final OkHttpClient wrappedClient;

  HostBoundWrappedHttpClient(HttpHost boundHost, OkHttpClient wrappedClient) {
    this.boundHost = boundHost;
    this.wrappedClient = wrappedClient;
  }

  @Override public Call newCall(Request request) {
    URL boundUrl;
    try {
      boundUrl =  new URL("https", boundHost.getHostName(), boundHost.getPort(), request.urlString());
    } catch (MalformedURLException e) {
      throw Throwables.propagate(e);
    }
    Request newRequest = request.newBuilder()
        .url(boundUrl)
        .build();
    return wrappedClient.newCall(newRequest);
  }
}
