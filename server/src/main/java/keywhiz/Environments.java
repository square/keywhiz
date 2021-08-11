package keywhiz;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import javax.validation.Validation;
import javax.validation.Validator;
import keywhiz.KeywhizConfig;

public final class Environments {
  private Environments() {}

  public static Environment fromBootstrap(Bootstrap<KeywhizConfig> bootstrap) {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    return new Environment(
        bootstrap.getApplication().getName(),
        bootstrap.getObjectMapper().copy(),
        validator,
        bootstrap.getMetricRegistry(),
        bootstrap.getClassLoader());
  }
}
