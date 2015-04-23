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
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.dropwizard.jackson.Jackson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.http.Cookie;
import org.apache.http.HttpHost;
import org.eclipse.jetty.server.CookieCutter;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.stream.Collectors.toList;

/**
 * Utility class for configuring KeywhizClients
 */

public class ClientUtils {
  private static final ObjectMapper mapper = Jackson.newObjectMapper();

  /**
   * Creates a {@link OkHttpClient} to start a TLS connection.
   *
   * @param cookies list of cookies to include in the client.
   * @return new http client.
   */
  public static OkHttpClient sslOkHttpClient(List<HttpCookie> cookies) {
    checkNotNull(cookies);

    SSLContext sslContext;
    try {
      TrustManagerFactory trustManagerFactory = TrustManagerFactory
          .getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

      sslContext = SSLContext.getInstance("TLSv1.2");
      sslContext.init(new KeyManager[0], trustManagers, new SecureRandom());
    } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
      throw Throwables.propagate(e);
    }

    SSLSocketFactory socketFactory = sslContext.getSocketFactory();

    OkHttpClient client = new OkHttpClient()
        .setSslSocketFactory(socketFactory)
        .setConnectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS))
        .setFollowSslRedirects(false);

    client.setRetryOnConnectionFailure(false);
    client.networkInterceptors()
        .add(new XsrfTokenInterceptor("XSRF-TOKEN", "X-XSRF-TOKEN"));

    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    cookies.forEach(c -> cookieManager.getCookieStore().add(null, c));

    client.setCookieHandler(cookieManager);
    return client;
  }

  /**
   * Wraps a {@link OkHttpClient} to only connect to URLs bound to the given host
   *
   * @param host domain and port to direct requests to.
   * @param wrappedClient {@link OkHttpClient} that will send requests.
   * @return new http client.
   */
  public static OkHttpClient hostBoundWrappedHttpClient(HttpHost host, OkHttpClient wrappedClient) {
    return new OkHttpClient() {
      @Override public Call newCall(Request request) {
        URL boundUrl;

        try {
          boundUrl = new URL("https", host.getHostName(), host.getPort(), request.urlString());
        } catch (MalformedURLException e) {
          throw Throwables.propagate(e);
        }
        Request newRequest = request.newBuilder()
            .url(boundUrl)
            .build();

        return wrappedClient.newCall(newRequest);
      }
    };
  }

  /**
   * Serialize the cookies to JSON from the given CookieManager to a file at the specified path.
   * Output file will have 660 permissions (owner-read, owner-write).
   *
   * @param cookieManager CookieManager that contains cookies to be serialized.
   * @param path Location to serialize cookies to file.
   */
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

  /**
   * Load cookies from the specified file from JSON to a name to value mapping.
   *
   * @param path Location of serialized cookies to load.
   * @return list of cookies that were read
   * @throws IOException
   */
  public static List<HttpCookie> loadCookies(Path path) throws IOException {
    TypeReference cookiesType = new TypeReference<List<JsonCookie>>() {};
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
      return System.console().readPassword();
    } else {
      throw new RuntimeException("Please login by running a command without piping.\n"
          + "For example: keywhiz.cli login");
    }
  }

  /**
   * HttpClient request interceptor to handle server-side XSRF protection.
   *
   *
   * If the server set a cookie with a specified name, the client will send a header with each
   * request with a specified name and value of the server-supplied cookie.
   */
  public static class XsrfTokenInterceptor implements Interceptor {
    private final String xsrfCookieName;
    private final String xsrfHeaderName;

    public XsrfTokenInterceptor(String xsrfCookieName, String xsrfHeaderName) {
      checkArgument(!xsrfCookieName.isEmpty());
      checkArgument(!xsrfHeaderName.isEmpty());

      this.xsrfCookieName = xsrfCookieName;
      this.xsrfHeaderName = xsrfHeaderName;
    }

    @Override public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      for (String header : request.headers(HttpHeaders.COOKIE)) {
        CookieCutter cookieCutter = new CookieCutter();
        cookieCutter.addCookieField(header);

        for (Cookie cookie : cookieCutter.getCookies()) {
          if (cookie.getName().equals(xsrfCookieName)) {
            request = request.newBuilder()
                .addHeader(xsrfHeaderName, cookie.getValue())
                .build();
          }
        }
      }
      return chain.proceed(request);
    }
  }
}
