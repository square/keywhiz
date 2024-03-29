package keywhiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.setup.Bootstrap;
import java.io.File;
import java.io.IOException;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class KeywhizConfigTest {
  private static final String PROPERTY_PREFIX = "dw";
  private static final String TEST_CONFIGS_PREFIX = "configs/";

  @Test
  public void parsesRowHmacCheckDisabled() {
    KeywhizConfig config = loadConfig("row-hmac-check-disabled.yaml");
    assertEquals(KeywhizConfig.RowHmacCheck.DISABLED, config.getRowHmacCheck());
  }

  @Test
  public void parsesRowHmacCheckEnforced() {
    KeywhizConfig config = loadConfig("row-hmac-check-enforced.yaml");
    assertEquals(KeywhizConfig.RowHmacCheck.ENFORCED, config.getRowHmacCheck());
  }

  @Test
  public void parsesRowHmacCheckLogging() {
    KeywhizConfig config = loadConfig("row-hmac-check-logging.yaml");
    assertEquals(KeywhizConfig.RowHmacCheck.DISABLED_BUT_LOG, config.getRowHmacCheck());
  }

  @Test
  public void parsesMaximumSecretLength() {
    KeywhizConfig config = loadConfig("with-secret-size-limit-123456.yaml");
    assertEquals(Long.valueOf(123456), config.getMaximumSecretSizeInBytesInclusive());
  }

  @Test
  public void missingMaximumSecretLengthIsNull() {
    KeywhizConfig config = loadConfig("without-secret-size-limit.yaml");
    assertNull(config.getMaximumSecretSizeInBytesInclusive());
  }

  @Test
  public void parseMissingNewSecretOwnershipStrategyDefaultsToNone() {
    KeywhizConfig config = loadConfig("new-secret-ownership-strategy-missing.yaml");
    assertThat(config.getNewSecretOwnershipStrategy()).isEqualTo(
        KeywhizConfig.NewSecretOwnershipStrategy.NONE);
  }

  @Test
  public void parseNewSecretOwnershipStrategyNone() {
    KeywhizConfig config = loadConfig("new-secret-ownership-strategy-none.yaml");
    assertThat(config.getNewSecretOwnershipStrategy()).isEqualTo(
        KeywhizConfig.NewSecretOwnershipStrategy.NONE);
  }

  @Test
  public void parseNewSecretOwnershipStrategyInfer() {
    KeywhizConfig config = loadConfig("new-secret-ownership-strategy-infer.yaml");
    assertThat(config.getNewSecretOwnershipStrategy()).isEqualTo(
        KeywhizConfig.NewSecretOwnershipStrategy.INFER_FROM_CLIENT);
  }

  @Test
  public void handleReservedPrefixes() {
    KeywhizConfig config = loadConfig("with-reserved-prefixes.yaml");
    assertThat(config.canCreateSecretWithName("any-secret-name", "any-owner-name")).isTrue();

    assertThat(config.canCreateSecretWithName("reserved-prefix", "any-owner-name")).isTrue();
    assertThat(config.canCreateSecretWithName("reserved-prefix", "reservedOwner")).isTrue();

    assertThat(config.canCreateSecretWithName("reserved-prefix:", "any-owner-name")).isFalse();
    assertThat(config.canCreateSecretWithName("reserved-prefix:", "reservedOwner")).isTrue();

    assertThat(config.canCreateSecretWithName("reserved-prefix:secretName", "any-owner-name")).isFalse();
    assertThat(config.canCreateSecretWithName("reserved-prefix:secretName", "reservedOwner")).isTrue();

    assertThat(config.canCreateSecretWithName("extra-prefix-reserved-prefix:secretName", "any-owner-name")).isTrue();
    assertThat(config.canCreateSecretWithName("extra-prefix-reserved-prefix:secretName", "reservedOwner")).isTrue();

    assertThat(config.canCreateSecretWithName("reserved-prefix:extra:secretName", "reservedOwner")).isFalse();
    assertThat(config.canCreateSecretWithName("reserved-prefix:extra:secretName", "anotherOwner")).isFalse();

    assertThat(config.canCreateSecretWithName("ab", "reservedOwner")).isTrue();
    assertThat(config.canCreateSecretWithName("ab", "noColonInPrefix")).isTrue();

    assertThat(config.canCreateSecretWithName("abc", "reservedOwner")).isFalse();
    assertThat(config.canCreateSecretWithName("abc", "noColonInPrefix")).isTrue();

    assertThat(config.canCreateSecretWithName("abcdef", "reservedOwner")).isFalse();
    assertThat(config.canCreateSecretWithName("abcdef", "noColonInPrefix")).isTrue();
  }

  private static KeywhizConfig loadConfig(String resource) {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    ObjectMapper objectMapper = createObjectMapper();
    File yamlFile = new File(Resources.getResource(TEST_CONFIGS_PREFIX + resource).getFile());

    KeywhizConfig config;
    try {
      config = new YamlConfigurationFactory<>(
          KeywhizConfig.class,
          validator,
          objectMapper,
          PROPERTY_PREFIX)
          .build(yamlFile);
    } catch (IOException | ConfigurationException e) {
      throw new RuntimeException(e);
    }

    return config;
  }

  private static ObjectMapper createObjectMapper() {
    KeywhizService service = new KeywhizService();
    Bootstrap<KeywhizConfig> bootstrap = new Bootstrap<>(service);
    service.initialize(bootstrap);
    ObjectMapper objectMapper = bootstrap.getObjectMapper().copy();
    return objectMapper;
  }
}
