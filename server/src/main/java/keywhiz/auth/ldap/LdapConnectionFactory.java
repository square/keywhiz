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
import com.unboundid.ldap.sdk.LDAPException;
import javax.net.ssl.SSLSocketFactory;

public class LdapConnectionFactory {
  private final String server;
  private final int port;
  private final String userDN;
  private final String password;

  public LdapConnectionFactory(String server, int port, String userDN, String password) {
    this.server = server;
    this.port = port;
    this.userDN = userDN;
    this.password = password;
  }

  public LDAPConnection getLDAPConnection() throws LDAPException {
    return getLDAPConnection(userDN, password);
  }

  public LDAPConnection getLDAPConnection(String userDN, String password) throws LDAPException {
    LDAPConnection ldapConnection = new LDAPConnection(SSLSocketFactory.getDefault());

    // Connect, retrieve the DN of the user (if any)
    ldapConnection.connect(server, port);
    ldapConnection.bind(userDN, password);

    return ldapConnection;
  }
}
