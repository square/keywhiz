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
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import org.apache.http.conn.ssl.SSLContextBuilder;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Helper methods for creating {@link OkHttpClient}s for testing.
 *
 * ONLY USE IN TEST. Assumptions are made which are not safe in production.
 */
public class HttpClients {
  private HttpClients() {}

  /**
   * Builds a localhost URL for testing given a path.
   */
  public static HttpUrl testUrl(String path) {
    String urlString = "https://localhost:4445" + path;
    HttpUrl url = HttpUrl.parse(urlString);
    checkState(url != null, "URL %s invalid", urlString);
    return url;
  }

  /**
   * Create a {@link OkHttpClient} for tests.
   *
   * @param keyStore Use a client certificate from keystore if present. Client certs disabled if null.
   * @param keyStorePassword keyStore password. Client certs disabled if null.
   * @param requestInterceptors Any request interceptors to register with client.
   * @return new http client
   */
  private static OkHttpClient testSslClient(
      @Nullable KeyStore keyStore,
      @Nullable String keyStorePassword,
      KeyStore trustStore,
      List<Interceptor> requestInterceptors) {

    boolean usingClientCert = keyStore != null && keyStorePassword != null;

    SSLContext sslContext;
    try {
      SSLContextBuilder sslContextBuilder = new SSLContextBuilder()
          .useProtocol("TLSv1.2")
          .loadTrustMaterial(trustStore);

      if (usingClientCert) {
        sslContextBuilder.loadKeyMaterial(keyStore, keyStorePassword.toCharArray());
      }

      sslContext = sslContextBuilder.build();
    } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
      throw Throwables.propagate(e);
    }

    OkHttpClient.Builder client = new OkHttpClient().newBuilder()
        .sslSocketFactory(sslContext.getSocketFactory())
        .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS))
        .followSslRedirects(false);

    client.followRedirects(false);
    client.retryOnConnectionFailure(false);

    // Won't use cookies and a client certificate at once.
    if (!usingClientCert) {
      CookieManager cookieManager = new CookieManager();
      cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
      client.cookieJar(new JavaNetCookieJar(cookieManager));
    }

    for (Interceptor interceptor : requestInterceptors) {
      client.networkInterceptors().add(interceptor);
    }

    return client.build();
  }

  public static TestClientBuilder builder() {
    return new TestClientBuilder();
  }

  public static class TestClientBuilder {
    private KeyStore keyStore;
    private String password;
    private List<Interceptor> requestInterceptors = new ArrayList<>();

    private TestClientBuilder() {}

    public TestClientBuilder withClientCert(KeyStore keyStore, String password) {
      this.keyStore = keyStore;
      this.password = password;
      return this;
    }

    public TestClientBuilder addRequestInterceptors(Interceptor first, Interceptor... others) {
      checkNotNull(first);
      requestInterceptors.add(first);
      requestInterceptors.addAll(Arrays.asList(others));
      return this;
    }

    public OkHttpClient build(KeyStore trustStore) {
      return testSslClient(keyStore, password, trustStore, requestInterceptors);
    }
  }
}
