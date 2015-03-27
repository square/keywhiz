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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import keywhiz.auth.cookie.CookieConfig;
import org.junit.Before;
import org.junit.Test;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class XsrfProtectionTest {
  private static final String COOKIE_NAME = "XSRF-TOKEN";

  XsrfProtection xsrfProtection;

  @Before public void setUp() {
    CookieConfig config = new CookieConfig();
    config.setName(COOKIE_NAME);
    config.setHttpOnly(false);

    xsrfProtection = new XsrfProtection(config);
  }

  @Test public void cookieValidates() {
    String cookie = xsrfProtection.generate("session_cookie_string").toString();

    Pattern pattern = Pattern.compile(format("%s=(\\w+);.*", COOKIE_NAME));
    assertThat(cookie).matches(pattern);

    Matcher matcher = pattern.matcher(cookie);
    matcher.matches();
    String value = matcher.group(1);

    assertThat(XsrfProtection.isValid(value, "session_cookie_string")).isTrue();
  }

  @Test(expected = IllegalArgumentException.class)
  public void cookieIsNotHttpOnly() {
    CookieConfig config = new CookieConfig();
    config.setHttpOnly(true);
    new XsrfProtection(config);
  }

  @Test(expected = NullPointerException.class)
  public void generateRejectsNulls() {
    xsrfProtection.generate(null);
  }

  @Test(expected = NullPointerException.class)
  public void verifyRejectsNulls() {
    XsrfProtection.isValid(null, null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void generateRejectsEmptyStrings() {
    xsrfProtection.generate("");
  }

  @Test(expected = IllegalArgumentException.class)
  public void verifyRejectsEmptyStrings() {
    XsrfProtection.isValid("", "");
  }
}
