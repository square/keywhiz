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
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

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
public class SecurityHeadersFilter implements ContainerResponseFilter {
  private static final long YEAR_OF_SECONDS = TimeUnit.DAYS.convert(365, TimeUnit.SECONDS);

  @Override public void filter(ContainerRequestContext request, ContainerResponseContext response)
      throws IOException {
    MultivaluedMap<String, Object> headers = response.getHeaders();

    // The X-* CSP headers are transitional and used by some versions of Firefox and Chrome.
    headers.add(HttpHeaders.CONTENT_SECURITY_POLICY, "default-src 'self'");
    headers.add("X-Content-Security-Policy", "default-src 'self'");
    headers.add("X-WebKit-CSP", "default-src 'self'");

    headers.add("Frame-Options", "DENY");
    headers.add(HttpHeaders.X_FRAME_OPTIONS, "DENY");

    headers.add(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");
    headers.add(HttpHeaders.X_XSS_PROTECTION, "1; mode=block");
    headers.add(HttpHeaders.STRICT_TRANSPORT_SECURITY,
        format("max-age=%d; includeSubDomains", YEAR_OF_SECONDS));
  }
}
