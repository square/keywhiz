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

package keywhiz.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.setup.Bootstrap;
import java.io.File;
import javax.validation.Validation;
import javax.validation.Validator;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizService;
import keywhiz.auth.ldap.LdapLookupConfig;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LdapLookupConfigTest {

  Bootstrap<KeywhizConfig> bootstrap;

  @Before public void setup() {
    KeywhizService service = new KeywhizService();
    bootstrap = new Bootstrap<>(service);
    service.initialize(bootstrap);
  }

  @Test public void parsesLDAPLookupCorrectly() throws Exception {
    File yamlFile = new File(Resources.getResource("fixtures/keywhiz-ldap-lookup-test.yaml").getFile());
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    ObjectMapper objectMapper = bootstrap.getObjectMapper().copy();

    LdapLookupConfig lookupConfig =
        new YamlConfigurationFactory<>(LdapLookupConfig.class, validator, objectMapper, "dw")
            .build(yamlFile);

    assertThat(lookupConfig.getRequiredRoles()).containsOnly("keywhizAdmins");
    assertThat(lookupConfig.getRoleBaseDN()).isEqualTo("ou=ApplicationAccess,dc=test,dc=com");
    assertThat(lookupConfig.getUserBaseDN()).isEqualTo("ou=people,dc=test,dc=com");
    assertThat(lookupConfig.getUserAttribute()).isEqualTo("uid");
  }
}
