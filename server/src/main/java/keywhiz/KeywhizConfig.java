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
package keywhiz;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import java.io.IOException;
import java.time.Duration;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import keywhiz.api.validation.ValidBase64;
import keywhiz.auth.UserAuthenticatorFactory;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.service.config.KeyStoreConfig;
import keywhiz.service.config.Templates;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotEmpty;

import static com.google.common.base.StandardSystemProperty.USER_NAME;

/**
 * Keywhiz app-level configuration. Ensures validation of entire configuration at startup.
 */
public class KeywhizConfig extends Configuration {
  @NotEmpty
  @JsonProperty
  private String environment;

  @NotNull
  @JsonProperty
  private TemplatedDataSourceFactory database = new TemplatedDataSourceFactory();

  @NotNull
  @JsonProperty
  private TemplatedDataSourceFactory readonlyDatabase = new TemplatedDataSourceFactory();

  @Valid
  @NotNull
  @JsonProperty
  private UserAuthenticatorFactory userAuth;

  @NotNull
  @JsonProperty
  private CookieConfig sessionCookie;

  @NotNull
  @JsonProperty
  private CookieConfig xsrfCookie;

  @NotNull
  @JsonProperty
  private String cookieKey;

  @Nullable
  @JsonProperty
  private String backupExportKey;

  @NotNull
  @JsonProperty
  private KeyStoreConfig contentKeyStore;

  @NotNull
  @JsonProperty
  private String derivationProviderClass = "com.sun.crypto.provider.SunJCE";

  @JsonProperty
  private String migrationsDir;

  @JsonProperty
  private String statusCacheExpiry;

  public String getEnvironment() {
    return environment;
  }

  /**
   * Customizes the database config when requested. If the username for the database is not set, the
   * current user is set as the username.
   *
   * @return DatabaseConfiguration for read/write database.
   */
  public DataSourceFactory getDataSourceFactory() {
    if (database.getUser() == null) {
      database.setUser(USER_NAME.value());
    }
    return database;
  }

  /**
   * Customizes the database config when requested. If the username for the database is not set, the
   * current user is set as the username.
   *
   * @return DatabaseConfiguration for readonly database.
   */
  public DataSourceFactory getReadonlyDataSourceFactory() {
    if (readonlyDatabase.getUser() == null) {
      readonlyDatabase.setUser(USER_NAME.value());
    }
    return readonlyDatabase;
  }

  /**
   * Customizes the migrations directory.
   *
   * The content of the directory might vary depending on the database engine. Having it
   * configurable is useful.
   *
   * @return path relative to the resources directory.
   */
  public String getMigrationsDir() {
    if (migrationsDir == null) {
      return "db/migration";
    }
    return migrationsDir;
  }

  public Duration getStatusCacheExpiry() {
    if ((statusCacheExpiry == null) || (statusCacheExpiry.isEmpty())) {
      // Default to 1 second
      return Duration.ofSeconds(1);
    }
    return Duration.parse(statusCacheExpiry);
  }

  /** @return LDAP configuration to authenticate admin users. Absent if fakeLdap is true. */
  public UserAuthenticatorFactory getUserAuthenticatorFactory() {
    return userAuth;
  }

  /** @return Configuration for authenticating session cookie provided by admin login. */
  public CookieConfig getSessionCookieConfig() {
    return sessionCookie;
  }

  /**
   * @return Configuration for cross-site request forgery (XSRF) prevention cookie.
   *
   * Admin clients must set an XSRF header for requests with the value of this cookie.
   */
  public CookieConfig getXsrfCookieConfig() {
    return xsrfCookie;
  }

  /** @return Base64-encoded key used to encrypt authenticating cookies. */
  // 256-bit key = 44 base64 characters
  @NotNull @ValidBase64 @Length(min = 44, max = 44)
  @JsonProperty
  public String getCookieKey() {
    try {
      return Templates.evaluateTemplate(cookieKey);
    } catch (IOException e) {
      throw new RuntimeException("Failure resolving cookieKey template", e);
    }
  }

  public KeyStoreConfig getContentKeyStore() {
    return contentKeyStore;
  }

  public String getDerivationProviderClass() {
    return derivationProviderClass;
  }

  @Nullable public String getBackupExportKey() {
    return backupExportKey;
  }

  public static class TemplatedDataSourceFactory extends DataSourceFactory {
    @Override public String getPassword() {
      try {
        return Templates.evaluateTemplate(super.getPassword());
      } catch (IOException e) {
        throw new RuntimeException("Failure resolving database password template", e);
      }
    }

    // Sets the evaluated password before calling the parent's create method.
    @Override public ManagedDataSource build(MetricRegistry metricRegistry, String name) {
      setPassword(getPassword());
      return super.build(metricRegistry, name);
    }
  }
}
