package keywhiz.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.ConfigurationFactory;
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
        new ConfigurationFactory<>(LdapLookupConfig.class, validator, objectMapper, "dw")
            .build(yamlFile);

    assertThat(lookupConfig.getRequiredRoles()).containsOnly("keywhizAdmins");
    assertThat(lookupConfig.getRoleBaseDN()).isEqualTo("ou=ApplicationAccess,dc=test,dc=com");
    assertThat(lookupConfig.getUserBaseDN()).isEqualTo("ou=people,dc=test,dc=com");
    assertThat(lookupConfig.getUserAttribute()).isEqualTo("uid");
  }
}
