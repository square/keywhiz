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
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Helper methods for creating {@link OkHttpClient}s for testing.
 *
 * ONLY USE IN TEST. Assumptions are made which are not safe in production.
 */
public class HttpClients {
  private HttpClients() {}

  /**
   * Bind a HttpClient to a specific destination host.
   *
   * @param boundHost host which all requests will be made against.
   * @param wrappedClient existing http client to wrap.
   * @return wrapped HttpClient.
   */
  public static OkHttpClient boundToHost(final HttpHost boundHost, final OkHttpClient wrappedClient) {
    checkNotNull(boundHost);
    checkNotNull(wrappedClient);
    return new HostBoundWrappedHttpClient(boundHost, wrappedClient);
  }

  /**
   * Creates strategy to only trust a single certificate regardless of signatures.
   *
   * @param certificate server certificate to trust.
   * @return TrustStrategy which only trusts the certificate.
   */
  public static TrustStrategy trustStrategy(final X509Certificate certificate) {
    checkNotNull(certificate);
    return (chain, authType) -> chain.length > 0 && chain[0].equals(certificate);
  }

  private static class LocalhostSslClientHostnameVerifier implements HostnameVerifier {
    @Override public boolean verify(String s, SSLSession sslSession) {
      return true;
    }
  }

  /**
   * Create a {@link OkHttpClient} which can only connect to localhost.
   *
   * @param port SSL port
   * @param keyStore Use a client certificate from keystore if present. Client certs disabled if null.
   * @param keyStorePassword keyStore password. Client certs disabled if null.
   * @param trustStrategy Trust strategy for servers, used in place of normal certificate validation.
   * @param requestInterceptors Any request interceptors to register with client.
   * @return new http client
   */
  private static OkHttpClient localhostSslClient(int port,
      @Nullable KeyStore keyStore,
      @Nullable String keyStorePassword,
      KeyStore trustStore,
      TrustStrategy trustStrategy,
      List<Interceptor> requestInterceptors) {

    boolean usingClientCert = keyStore != null && keyStorePassword != null;

    SSLContext sslContext;
    try {
      SSLContextBuilder sslContextBuilder = new SSLContextBuilder()
          .useProtocol("TLSv1.2")
          .loadTrustMaterial(trustStore, trustStrategy);

      if (usingClientCert) {
        sslContextBuilder.loadKeyMaterial(keyStore, keyStorePassword.toCharArray());
      }

      sslContext = sslContextBuilder.build();
    } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException | KeyManagementException e) {
      throw Throwables.propagate(e);
    }

    SSLSocketFactory socketFactory = sslContext.getSocketFactory();

    OkHttpClient client = new OkHttpClient()
        .setSslSocketFactory(socketFactory)
        .setConnectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS))
        .setFollowSslRedirects(false)
        .setHostnameVerifier(new LocalhostSslClientHostnameVerifier());

    client.setFollowRedirects(false);
    client.setRetryOnConnectionFailure(false);

    // Won't use cookies and a client certificate at once.
    if (!usingClientCert) {
      CookieManager cookieManager = new CookieManager();
      cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
      client.setCookieHandler(cookieManager);
    }

    for (Interceptor interceptor : requestInterceptors) {
      client.networkInterceptors().add(interceptor);
    }

    // Only connects to localhost.
    return boundToHost(new HttpHost("localhost", port, "https"), client);
  }

  public static LocalhostClientBuilder builder() {
    return new LocalhostClientBuilder();
  }

  public static class LocalhostClientBuilder {
    private KeyStore keyStore;
    private String password;
    private List<Interceptor> requestInterceptors = new ArrayList<>();

    private LocalhostClientBuilder() {
    }

    public LocalhostClientBuilder withClientCert(KeyStore keyStore, String password) {
      this.keyStore = keyStore;
      this.password = password;
      return this;
    }

    public LocalhostClientBuilder addRequestInterceptors(Interceptor first,
        Interceptor... others) {
      checkNotNull(first);
      requestInterceptors.add(first);
      requestInterceptors.addAll(Arrays.asList(others));
      return this;
    }

    public OkHttpClient build(X509Certificate serverCertificate, int port) {
      TrustStrategy trustStrategy = HttpClients.trustStrategy(serverCertificate);
      KeyStore trustStore;
      try {
        trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, "changeit".toCharArray());
        trustStore.setCertificateEntry("serverCert", serverCertificate);
      } catch (CertificateException| IOException | KeyStoreException | NoSuchAlgorithmException e) {
        throw Throwables.propagate(e);
      }

      return localhostSslClient(port, keyStore, password, trustStore, trustStrategy, requestInterceptors);
    }
  }
}
