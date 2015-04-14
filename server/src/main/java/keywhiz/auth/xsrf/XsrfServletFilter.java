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

import com.google.common.collect.ImmutableSet;
import javax.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.SessionCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;

@Singleton
public class XsrfServletFilter implements Filter {
  private static final Logger logger = LoggerFactory.getLogger(XsrfServletFilter.class);
  private static final Set<String> EXCLUDED_PATHS = ImmutableSet.of("/admin/login", "/admin/logout");

  private final String sessionCookieName;
  private final String xsrfHeaderName;

  @Inject
  public XsrfServletFilter(
      @SessionCookie CookieConfig sessionCookieConfig,
      @Xsrf String xsrfHeaderName) {
    this.sessionCookieName = sessionCookieConfig.getName();
    this.xsrfHeaderName = xsrfHeaderName;
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    // Exclude certain paths.
    if (EXCLUDED_PATHS.contains(request.getRequestURI())) {
      chain.doFilter(servletRequest, response);
      return;
    }

    String xsrfHeader = request.getHeader(xsrfHeaderName);
    String sessionCookie = null;

    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(sessionCookieName)) {
          sessionCookie = cookie.getValue();
        }
      }
    }

    if (isNullOrEmpty(xsrfHeader)) {
      logger.warn("Request missing {} header", xsrfHeaderName);
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    if (isNullOrEmpty(sessionCookie)) {
      logger.warn("Request missing {} cookie", sessionCookieName);
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    if (!XsrfProtection.isValid(xsrfHeader, sessionCookie)) {
      logger.warn("Invalid {} header in request: {}", xsrfHeaderName, xsrfHeader);
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    chain.doFilter(servletRequest, response);
  }

  @Override public void init(FilterConfig filterConfig) throws ServletException {}

  @Override public void destroy() {}
}
