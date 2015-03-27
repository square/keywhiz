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
package keywhiz.auth.mutualssl;

import java.io.IOException;
import java.security.cert.X509Certificate;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

/**
 * If a client uses a certificate to authenticate itself, this class provides a resource filter to
 * pass the client's certificate chain on for a resource. This is best used in a injection provider.
 *
 * NOTE: ClientCertificateFilter will NOT authenticate the client's certificate chain, just pass it
 * along.
 *
 * <pre>
 * {code
 * HttpContext c;
 * Principal p = c.getRequest().getUserPrincipal();
 * if (p instanceof CertificatePrincipal) &#123;
 *   ImmutableList&lt;X509Certificate&gt; certs = ((CertificatePrincipal) p).getCertificateChain();
 * &#125;
 * }
 * </pre>
 */
@Priority(Priorities.AUTHENTICATION)
public class ClientCertificateFilter implements ContainerRequestFilter {
  @Override public void filter(ContainerRequestContext context) throws IOException {
    X509Certificate[] chain =
        (X509Certificate[]) context.getProperty("javax.servlet.request.X509Certificate");

    if (chain != null && chain.length > 0) {
      String subject = chain[0].getSubjectDN().getName();
      CertificateSecurityContext securityContext = new CertificateSecurityContext(subject, chain);
      context.setSecurityContext(securityContext);
    }
  }
}
