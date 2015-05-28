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
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import keywhiz.auth.cookie.CookieConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class XsrfServletFilterTest {
  private static final String COOKIE_NAME = "session";
  private static final String HEADER_NAME = "X-XSRF-TOKEN";

  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  @Mock HttpServletRequest mockRequest;
  @Mock HttpServletResponse mockResponse;
  @Mock FilterChain mockChain;

  XsrfServletFilter filter;
  XsrfProtection xsrfProtection;

  @Before public void setUp() {
    CookieConfig config = new CookieConfig();
    config.setName(COOKIE_NAME);
    config.setHttpOnly(false);

    filter = new XsrfServletFilter(config, HEADER_NAME);
    xsrfProtection = new XsrfProtection(config);
  }

  @Test public void rejectsMissingHeader() throws Exception {
    when(mockRequest.getCookies()).thenReturn(new Cookie[] {new Cookie(COOKIE_NAME, "content")});

    filter.doFilter(mockRequest, mockResponse, mockChain);

    verify(mockResponse).sendError(401);
    verify(mockChain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
  }

  @Test public void rejectsMissingCookie() throws Exception {
    when(mockRequest.getCookies()).thenReturn(new Cookie[] {});
    when(mockRequest.getHeader(HEADER_NAME)).thenReturn("content");

    filter.doFilter(mockRequest, mockResponse, mockChain);

    verify(mockResponse).sendError(401);
    verify(mockChain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
  }

  @Test public void rejectsNullCookies() throws Exception {
    when(mockRequest.getCookies()).thenReturn(null);
    when(mockRequest.getHeader(HEADER_NAME)).thenReturn("content");

    filter.doFilter(mockRequest, mockResponse, mockChain);

    verify(mockResponse).sendError(401);
    verify(mockChain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
  }

  @Test public void rejectsInvalidHeader() throws Exception {
    when(mockRequest.getCookies()).thenReturn(new Cookie[] {new Cookie(COOKIE_NAME, "content")});
    when(mockRequest.getHeader(HEADER_NAME)).thenReturn("content");

    filter.doFilter(mockRequest, mockResponse, mockChain);

    verify(mockResponse).sendError(401);
    verify(mockChain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
  }

  @Test public void continuesValidHeader() throws Exception {
    String sessionCookie = xsrfProtection.generate("some session").toString();
    Matcher matcher = Pattern.compile(COOKIE_NAME + "=([^;]+);.*").matcher(sessionCookie);
    matcher.matches();

    when(mockRequest.getCookies()).thenReturn(new Cookie[] {new Cookie(COOKIE_NAME, "some session")});
    when(mockRequest.getHeader(HEADER_NAME)).thenReturn(matcher.group(1));

    filter.doFilter(mockRequest, mockResponse, mockChain);

    verify(mockChain).doFilter(mockRequest, mockResponse);
  }
}
