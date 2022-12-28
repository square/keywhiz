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

package keywhiz.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.jackson.Jackson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.ConnectionSpec;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.net.CookiePolicy.ACCEPT_ALL;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.stream.Collectors.toList;

/**
 * Utility class for configuring KeywhizClients
 */

public class ClientUtils {
  private static final ObjectMapper mapper = Jackson.newObjectMapper();
  private static CookieManager cookieManager = null;

  public static synchronized CookieManager getCookieManager() {
    if (cookieManager != null) {
      return cookieManager;
    }
    cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(ACCEPT_ALL);
    return cookieManager;
  }

  /**
   * Creates a {@link OkHttpClient} to start a TLS connection.
   *
   * @param devTrustStore if not null, uses the provided TrustStore instead of whatever is
   *                      configured in the JVM. This is a convenient way to allow developers to
   *                      start playing with Keywhiz right away. This option should not be used in
   *                      production systems.
   * @param cookies list of cookies to include in the client.
   * @return new http client.
   */
  public static OkHttpClient sslOkHttpClient(@Nullable KeyStore devTrustStore,
      List<HttpCookie> cookies) {
    checkNotNull(cookies);

    SSLContext sslContext;
    X509TrustManager trustManager;
    TrustManager[] trustAllCerts;
    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory
          .getInstance(TrustManagerFactory.getDefaultAlgorithm());

      trustManagerFactory.init(devTrustStore);

      trustAllCerts = trustManagerFactory.getTrustManagers();
      trustManager = (X509TrustManager) trustAllCerts[0];

      sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new SecureRandom());
    } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
      throw Throwables.propagate(e);
    }

    SSLSocketFactory socketFactory = sslContext.getSocketFactory();
    OkHttpClient.Builder newBuilder = new OkHttpClient.Builder();
    newBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
    newBuilder.hostnameVerifier((hostname, session) -> true);

    newBuilder.retryOnConnectionFailure(false);

    cookies.forEach(c -> getCookieManager().getCookieStore().add(null, c));
    newBuilder.cookieJar(new JavaNetCookieJar(getCookieManager()));
    return newBuilder.build();
  }

  /**
   * Serialize the cookies to JSON from the given CookieManager to a file at the specified path.
   * Output file will have 660 permissions (owner-read, owner-write).
   *
   * @param cookieManager CookieManager that contains cookies to be serialized.
   * @param path Location to serialize cookies to file.
   */
  @VisibleForTesting
  public static void saveCookies(CookieManager cookieManager, Path path) {
    List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
    List<JsonCookie> jsonCookies = cookies.stream()
        .map(JsonCookie::fromHttpCookie)
        .collect(toList());

    try (BufferedWriter writer = Files.newBufferedWriter(path, CREATE)) {
      Files.setPosixFilePermissions(path, ImmutableSet.of(OWNER_READ, OWNER_WRITE));
      writer.write(mapper.writeValueAsString(jsonCookies));
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  public static void saveCookies(Path path) {
    saveCookies(getCookieManager(), path);
  }

  /**
   * Load cookies from the specified file from JSON to a name to value mapping.
   *
   * @param path Location of serialized cookies to load.
   * @return list of cookies that were read
   * @throws IOException
   */
  public static List<HttpCookie> loadCookies(Path path) throws IOException {
    TypeReference<List<JsonCookie>> cookiesType = new TypeReference<>() {};
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      List<JsonCookie> jsonCookies = mapper.readValue(reader, cookiesType);
      return jsonCookies.stream()
          .map(JsonCookie::toHttpCookie)
          .collect(toList());
    }
  }

  /**
   * Read password from console.
   *
   * Note that when the System.console() is null, there is no secure way of entering a password
   * without exposing it in the clear on the console (it is echoed onto the screen).
   *
   * For this reason, it is suggested that the user login prior to using functionality such as
   * input redirection since this could result in a null console.
   *
   * @param user who we are prompting a password for
   * @return user-inputted password
   */
  public static char[] readPassword(String user) {
    Console console = System.console();
    if (console != null) {
      System.out.format("password for '%s': ", user);
      return console.readPassword();
    } else {
      throw new RuntimeException("Please login by running a command without piping.\n"
          + "For example: keywhiz.cli login");
    }
  }
}
