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

package keywhiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.ws.rs.core.MediaType;
import keywhiz.client.KeywhizClient;
import keywhiz.testing.HttpClients;

public class TestClients {
  private TestClients() {}

  private static final HttpUrl TEST_URL = HttpUrl.parse("https://localhost:4445/");

  public static OkHttpClient unauthenticatedClient() {
    String password = "ponies";
    KeyStore trustStore = keyStoreFromResource("dev_and_test_truststore.p12", password);

    return HttpClients.builder()
        .addRequestInterceptors(
            new AuthHelper.XsrfRequestInterceptor("XSRF-TOKEN", "X-XSRF-TOKEN"),
            new AuthHelper.AcceptRequestInterceptor(MediaType.APPLICATION_JSON))
        .build(trustStore);
  }

  public static OkHttpClient mutualSslClient() {
    String password = "ponies";
    KeyStore keyStore = keyStoreFromResource("clients/client.p12", password);
    KeyStore trustStore = keyStoreFromResource("dev_and_test_truststore.p12", password);

    return HttpClients.builder()
        .withClientCert(keyStore, password)
        .addRequestInterceptors(new AuthHelper.AcceptRequestInterceptor(MediaType.APPLICATION_JSON))
        .build(trustStore);
  }

  /** Provides a client certificate authenticated client which has no assigned secrets. */
  public static OkHttpClient noSecretsClient() {
    String password = "ponies";
    KeyStore keyStore = keyStoreFromResource("clients/noSecretsClient.p12", password);
    KeyStore trustStore = keyStoreFromResource("dev_and_test_truststore.p12", password);

    return HttpClients.builder()
        .withClientCert(keyStore, password)
        .addRequestInterceptors(new AuthHelper.AcceptRequestInterceptor(MediaType.APPLICATION_JSON))
        .build(trustStore);
  }

  public static OkHttpClient noCertNoXsrfClient() {
    String password = "ponies";
    KeyStore trustStore = keyStoreFromResource("dev_and_test_truststore.p12", password);

    return HttpClients.builder()
        .addRequestInterceptors(new AuthHelper.AcceptRequestInterceptor(MediaType.APPLICATION_JSON))
        .build(trustStore);
  }

  public static KeywhizClient keywhizClient() {
    String password = "ponies";
    KeyStore trustStore = keyStoreFromResource("dev_and_test_truststore.p12", password);

    OkHttpClient httpClient = HttpClients.builder()
        .addRequestInterceptors(
            new AuthHelper.XsrfRequestInterceptor("XSRF-TOKEN", "X-XSRF-TOKEN"),
            new AuthHelper.AcceptRequestInterceptor(MediaType.APPLICATION_JSON))
        .build(trustStore);

    ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());
    return new KeywhizClient(mapper, httpClient, TEST_URL);
  }

  private static KeyStore keyStoreFromResource(String path, String password) {
    KeyStore keyStore;
    try (InputStream stream = Resources.getResource(path).openStream()) {
      keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(stream, password.toCharArray());
    } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
      throw new AssertionError(e);
    }
    return keyStore;
  }
}
