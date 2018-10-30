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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.auto.service.AutoService;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import keywhiz.api.validation.ValidX500Name;
import keywhiz.auth.User;
import keywhiz.auth.UserAuthenticatorFactory;
import keywhiz.service.config.Templates;
import org.hibernate.validator.constraints.NotEmpty;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration parameters for using an LDAP connection. */
@AutoService(UserAuthenticatorFactory.class)
@JsonTypeName("ldap")
@SuppressWarnings("unused")
public class LdapAuthenticatorFactory implements UserAuthenticatorFactory {
  private static final Logger logger = LoggerFactory.getLogger(LdapAuthenticatorFactory.class);

  @NotEmpty
  private String server;

  @Min(value = 1) @Max(value = 65535)
  private int port = 636;

  /**
   * LDAP uses X.500 names, so the nomenclature is a bit odd. This is essentially the username to
   * use but should be a fully-qualified X.500 name.
   */
  @ValidX500Name
  private String userDN;

  private String password;

  /**
   * LDAP parameters to lookup authenticated users and their roles.
   */
  @NotNull @Valid
  private LdapLookupConfig lookup;

  /** Trust store options for LDAP */
  @NotEmpty
  private String trustStorePath;

  @NotEmpty
  private String trustStorePassword;

  @NotEmpty
  private String trustStoreType;

  public String getServer() {
    return server;
  }

  public int getPort() {
    return port;
  }

  public String getUserDN() {
    return userDN;
  }

  public String getTrustStorePath() {
    return trustStorePath;
  }

  public String getTrustStorePassword() {
    return trustStorePassword;
  }

  public String getTrustStoreType() {
    return trustStoreType;
  }

  @NotEmpty
  public String getPassword() {
    try {
      return Templates.evaluateTemplate(password);
    } catch (IOException e) {
      throw new RuntimeException("Failure resolving ldap password template", e);
    }
  }

  public LdapLookupConfig getLookup() {
    return lookup;
  }

  // TODO: Ldap takes a DSLContext but doesn't use it. We could remove this dependency. Not sure
  // it really matters since we need a DSLContext for all the other data.
  // https://github.com/square/keywhiz/issues/39
  @Override public Authenticator<BasicCredentials, User> build(DSLContext dslContext) {
    logger.debug("Creating LDAP authenticator");
    LdapConnectionFactory connectionFactory =
        new LdapConnectionFactory(getServer(), getPort(), getUserDN(), getPassword(),
            getTrustStorePath(), getTrustStorePassword(), getTrustStoreType());
    return new LdapAuthenticator(connectionFactory, getLookup());
  }
}
