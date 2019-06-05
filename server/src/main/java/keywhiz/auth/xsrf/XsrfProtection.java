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
package keywhiz.auth.xsrf;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import javax.inject.Inject;
import javax.ws.rs.core.NewCookie;
import keywhiz.auth.Subtles;
import keywhiz.auth.cookie.CookieConfig;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Response;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Cross-site request forgery (XSRF or CSRF) is an attack which lets an attacker trigger requests in
 * your browser to a sensitive site you're already logged in to.
 *
 * For example, you're already logged into example.com and an attacker causes your browser to make a
 * POST request to example.com/changepassword. Once logged in to example.com, your browser
 * authenticates you by sending a session cookie. The cookie is sent with every request to
 * example.com which enables the attack.
 *
 * To prevent this vulnerability, you must ensure a sensitive request came from a page/location on
 * your domain. For typical sites, secret values are embedded in forms, which is not implemented
 * here. For sites that respond to AJAX (XHR) requests, it is sufficient to check a special header
 * value, in this case X-XSRF-TOKEN is used.
 *
 * Merely checking the existence of the header is often sufficient as origins cannot set header
 * values for other origins. However, there have been exceptions which warrant an unpredictable
 * value for the header. // TODO(justin): Include a link on flash vulns.
 *
 * This class generates a hash of a supplied session token. It encapsulates information about
 * hashing and the appropriate cookie and header values.
 */
public class XsrfProtection {
  private static final HashFunction SHA512 = Hashing.sha512();

  private final CookieConfig config;

  @Inject
  public XsrfProtection(@Xsrf CookieConfig config) {
    checkArgument(!config.isHttpOnly(), "XSRF cookies must not be HttpOnly.");
    this.config = config;
  }

  public NewCookie generate(String session) {
    checkArgument(!session.isEmpty());
    String cookieValue = SHA512.hashString(session, UTF_8).toString();

    // HttpOnly MUST NOT be present for this cookie.
    HttpCookie cookie = new HttpCookie(config.getName(), cookieValue, config.getDomain(),
        config.getPath(), -1, config.isHttpOnly(), config.isSecure());
    Response response = newResponse();
    response.addCookie(cookie);
    return NewCookie.valueOf(response.getHttpFields().get(HttpHeader.SET_COOKIE));
  }

  public static boolean isValid(String header, String session) {
    checkArgument(!header.isEmpty());
    checkArgument(!session.isEmpty());

    String expected = SHA512.hashString(session, UTF_8).toString();
    return Subtles.secureCompare(expected.getBytes(UTF_8), header.getBytes(UTF_8));
  }

  private Response newResponse() {
    return new Response(new HttpChannel(null, new HttpConfiguration(), null, null), null);
  }
}
