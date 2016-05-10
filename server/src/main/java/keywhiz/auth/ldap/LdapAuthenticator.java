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

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.java8.auth.Authenticator;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.ForbiddenException;
import keywhiz.auth.User;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdapAuthenticator implements Authenticator<BasicCredentials, User> {
  private static final Logger logger = LoggerFactory.getLogger(LdapAuthenticator.class);

  private final LdapConnectionFactory connectionFactory;
  private final LdapLookupConfig config;

  public LdapAuthenticator(LdapConnectionFactory connectionFactory, LdapLookupConfig config) {
    this.connectionFactory = connectionFactory;
    this.config = config;
  }

  @Override
  public Optional<User> authenticate(BasicCredentials credentials) {
    User user = null;

    try {
      String username = credentials.getUsername();
      if (!User.isSanitizedUsername(username)) {
        logger.info("Username: {} must match pattern: {}", username, User.USERNAME_PATTERN);
        return Optional.empty();
      }

      String userDN = dnFromUsername(username);
      String password = credentials.getPassword();

      // Must have password for current config
      if (Strings.isNullOrEmpty(password)) {
        logger.info("No password for user provided");
        return Optional.empty();
      }

      LDAPConnection authenticatedConnection = connectionFactory.getLDAPConnection(userDN, password);
      authenticatedConnection.close();

      Set<String> requiredRoles = config.getRequiredRoles();
      if (!requiredRoles.isEmpty()) {
        Set<String> roles = rolesFromDN(userDN);

        boolean accessAllowed = false;
        for (String requiredRole : requiredRoles) {
          if (roles.contains(requiredRole)) {
            accessAllowed = true;
          }
        }

        if (!accessAllowed) {
          logger.warn("User {} not in one of required LDAP roles: [{}].", username, requiredRoles);
          throw new ForbiddenException();
        }
      }

      user = User.named(username);
    } catch (LDAPException le) {
      // The INVALID_CREDENTIALS case is handled by returning an absent optional from this function
      if (le.getResultCode() != ResultCode.INVALID_CREDENTIALS) {
        logger.error("Error connecting to LDAP", le);
        throw Throwables.propagate(le);
      }
    } catch (GeneralSecurityException gse) {
        logger.error("TLS error connecting to LDAP", gse);
        throw Throwables.propagate(gse);
    }

    return Optional.ofNullable(user);
  }

  private String dnFromUsername(String username) throws LDAPException, GeneralSecurityException {
    String baseDN = config.getUserBaseDN();
    String lookup = String.format("(%s=%s)", config.getUserAttribute(), username);
    SearchRequest searchRequest = new SearchRequest(baseDN, SearchScope.SUB, lookup);

    LDAPConnection connection = connectionFactory.getLDAPConnection();
    try {
      SearchResult sr = connection.search(searchRequest);

      if (sr.getEntryCount() == 0) {
        throw new LDAPException(ResultCode.INVALID_CREDENTIALS);
      }

      return sr.getSearchEntries().get(0).getDN();
    } finally {
      connection.close();
    }
  }

  private Set<String> rolesFromDN(String userDN) throws LDAPException, GeneralSecurityException {
    SearchRequest searchRequest = new SearchRequest(config.getRoleBaseDN(),
        SearchScope.SUB, Filter.createEqualityFilter("uniqueMember", userDN));
    Set<String> roles = Sets.newLinkedHashSet();

    LDAPConnection connection = connectionFactory.getLDAPConnection();
    try {
      SearchResult sr = connection.search(searchRequest);

      for (SearchResultEntry sre : sr.getSearchEntries()) {
        X500Name x500Name = new X500Name(sre.getDN());
        RDN[] rdns = x500Name.getRDNs(BCStyle.CN);
        if (rdns.length == 0) {
          logger.error("Could not create X500 Name for role:" + sre.getDN());
        } else {
          String commonName = IETFUtils.valueToString(rdns[0].getFirst().getValue());
          roles.add(commonName);
        }
      }
    } finally {
      connection.close();
    }

    return roles;
  }
}
