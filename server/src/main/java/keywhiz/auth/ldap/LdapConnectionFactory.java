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
package keywhiz.auth.ldap;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.util.ssl.HostNameSSLSocketVerifier;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustStoreTrustManager;
import java.security.GeneralSecurityException;

public class LdapConnectionFactory {
  private final String server;
  private final int port;
  private final String userDN;
  private final String password;
  private final String trustStorePath;
  private final String trustStorePassword;
  private final String trustStoreType;

  public LdapConnectionFactory(String server, int port, String userDN, String password, String trustStorePath, String trustStorePassword, String trustStoreType) {
    this.server = server;
    this.port = port;
    this.userDN = userDN;
    this.password = password;
    this.trustStorePath = trustStorePath;
    this.trustStorePassword = trustStorePassword;
    this.trustStoreType = trustStoreType;
  }

  public LDAPConnection getLDAPConnection() throws LDAPException, GeneralSecurityException {
    return getLDAPConnection(userDN, password);
  }

  public LDAPConnection getLDAPConnection(String userDN, String password)
      throws LDAPException, GeneralSecurityException {
    TrustStoreTrustManager trust = new TrustStoreTrustManager(trustStorePath, trustStorePassword.toCharArray(), trustStoreType, false);
    LDAPConnectionOptions options = new LDAPConnectionOptions();
    options.setSSLSocketVerifier(new HostNameSSLSocketVerifier(false));
    SSLUtil sslUtil = new SSLUtil(trust);
    LDAPConnection ldapConnection = new LDAPConnection(sslUtil.createSSLSocketFactory("TLSv1.2"), options);

    // Connect, retrieve the DN of the user (if any)
    ldapConnection.connect(server, port);
    ldapConnection.bind(userDN, password);

    return ldapConnection;
  }
}
