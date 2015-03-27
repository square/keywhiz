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

import java.security.Principal;
import java.security.cert.X509Certificate;
import javax.ws.rs.core.SecurityContext;

/**
 * #getUserPrincipal() returns a {@link keywhiz.auth.mutualssl.CertificatePrincipal} to expose the client's certificate
 * chain. This can be useful for more granular authorization, for example on a resource.
 */
public class CertificateSecurityContext implements SecurityContext {
  private final CertificatePrincipal principal;

  public CertificateSecurityContext(String subjectDn, X509Certificate[] chain) {
    this.principal = new CertificatePrincipal(subjectDn, chain);
  }

  /**
   * After ensuring your {@link javax.ws.rs.core.SecurityContext} is an instance of CertificateSecurityContext,
   * cast the {@link java.security.Principal} to a {@link keywhiz.auth.mutualssl.CertificatePrincipal}.
   *
   * @return CertificatePrincipal principal type which includes the client's certificate chain.
   */
  @Override
  public Principal getUserPrincipal() {
    return principal;
  }

  @Override
  public boolean isUserInRole(String role) {
    return false;
  }

  @Override
  public boolean isSecure() {
    return true;
  }

  @Override
  public String getAuthenticationScheme() {
    return SecurityContext.CLIENT_CERT_AUTH;
  }
}
