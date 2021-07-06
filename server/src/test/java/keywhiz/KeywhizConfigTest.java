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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KeywhizConfigTest {
  private static final String PROPERTY_PREFIX = "dw";
  private static final String TEST_CONFIGS_PREFIX = "configs/";

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
