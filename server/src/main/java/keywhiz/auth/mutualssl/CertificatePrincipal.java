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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;

import static java.time.Instant.EPOCH;

public class CertificatePrincipal implements Principal {
  private final String subjectDn;
  private final ImmutableList<X509Certificate> certificateChain;

  public CertificatePrincipal(String subjectDn, X509Certificate[] chain) {
    this.subjectDn = subjectDn;
    certificateChain = ImmutableList.copyOf(chain);
  }

  public ImmutableList<X509Certificate> getCertificateChain() {
    return certificateChain;
  }

  public Instant getCertificateExpiration() {
    return certificateChain.stream()
        .map(c -> c.getNotAfter().toInstant())
        .min(Instant::compareTo)
        .orElse(EPOCH);
  }

  /**
   * @return string which should be a valid {@link sun.security.x509.X500Name}.
   */
  @Override
  public String getName() {
    return subjectDn;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subjectDn", subjectDn)
        .add("certificateChain", certificateChain)
        .toString();
  }
}
