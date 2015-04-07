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
package keywhiz.service.filters;

import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import static java.lang.String.format;

/**
 * Sets a variety of web security headers in every response.
 *
 * Included:
 *   Content Security Policy (CSP): http://www.w3.org/TR/CSP/
 *   Frame-Options: http://tools.ietf.org/html/draft-ietf-websec-frame-options-00,
 *                  http://tools.ietf.org/html/draft-ietf-websec-x-frame-options-01
 *   HTTP Strict Transport Security: http://tools.ietf.org/html/draft-ietf-websec-strict-transport-sec-14
 *   IE X-Content-Type-Options: http://msdn.microsoft.com/en-us/library/ie/gg622941(v=vs.85).aspx
 *   IE X-XSS-Protection: http://blogs.msdn.com/b/ie/archive/2008/07/02/ie8-security-part-iv-the-xss-filter.aspx
 */
public class SecurityHeadersFilter implements Filter {
  private static final long YEAR_OF_SECONDS = TimeUnit.SECONDS.convert(365, TimeUnit.DAYS);

  @Override public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    if (response instanceof HttpServletResponse) {
      HttpServletResponse r = (HttpServletResponse) response;

      // Defense against XSS. We don't care about IE's Content-Security-Policy because it's useless
      r.addHeader("X-Content-Security-Policy", "default-src 'self'");
      r.addHeader(HttpHeaders.X_XSS_PROTECTION, "0"); // With CSP, we don't need crazy magic

      // Tell IE not to do silly things
      r.addHeader(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");

      // Protection against click jacking
      r.addHeader("Frame-Options", "DENY"); // Who uses this?
      r.addHeader(HttpHeaders.X_FRAME_OPTIONS, "DENY");

      // https-all-the-time
      r.addHeader(HttpHeaders.STRICT_TRANSPORT_SECURITY,
          format("max-age=%d; includeSubDomains", YEAR_OF_SECONDS));
    }
    chain.doFilter(request, response);
  }

  @Override public void destroy() {
  }
}
