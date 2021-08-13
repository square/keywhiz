package keywhiz.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.io.File;
import java.io.IOException;
import javax.validation.Validation;
import javax.validation.Validator;
import keywhiz.KeywhizConfig;
import keywhiz.KeywhizService;

public class ServiceContext {
  private final KeywhizService service;
  private final Bootstrap<KeywhizConfig> bootstrap;
  private final KeywhizConfig config;
  private final Environment environment;

  private ServiceContext(
      KeywhizService service,
      Bootstrap<KeywhizConfig> bootstrap,
      KeywhizConfig config,
      Environment environment) {
    this.service = service;
    this.bootstrap = bootstrap;
    this.config = config;
    this.environment = environment;
  }

  public static ServiceContext create() {
    KeywhizService service = new KeywhizService();
    Bootstrap<KeywhizConfig> bootstrap = new Bootstrap<>(service);
    service.initialize(bootstrap);

    ObjectMapper objectMapper = bootstrap.getObjectMapper().copy();
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    KeywhizConfig config = loadTestConfig(objectMapper, validator);

    Environment environment = new Environment(
        bootstrap.getApplication().getName(),
        objectMapper,
        validator,
        bootstrap.getMetricRegistry(),
        bootstrap.getClassLoader());

    return new ServiceContext(service, bootstrap, config, environment);
  }

  public KeywhizService getService() { return service; }

  public Bootstrap<KeywhizConfig> getBootstrap() { return bootstrap; }

  public KeywhizConfig getConfig() { return config; }

  public Environment getEnvironment() { return environment; }

  private static KeywhizConfig loadTestConfig(ObjectMapper objectMapper, Validator validator) {
    File yamlFile = new File(Resources.getResource("keywhiz-test.yaml").getFile());
    try {
      return new YamlConfigurationFactory<>(KeywhizConfig.class, validator, objectMapper, "dw")
          .build(yamlFile);
    } catch (IOException | ConfigurationException e) {
      throw Throwables.propagate(e);
    }
  }
}
