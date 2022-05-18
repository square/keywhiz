package keywhiz.inject;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import keywhiz.KeywhizConfig;

public class ContextModule extends AbstractModule {
  private final KeywhizConfig config;
  private final Environment environment;
  private final MetricRegistry metricRegistry;

  public ContextModule(KeywhizConfig config, Environment environment) {
    this.config = config;
    this.environment = environment;
    this.metricRegistry = this.environment.metrics();
  }

  @Override protected void configure() {
    // TODO(justin): Consider https://github.com/HubSpot/dropwizard-guice.
    bind(Environment.class).toInstance(environment);
    bind(Configuration.class).toInstance(config);
    bind(KeywhizConfig.class).toInstance(config);
    bind(MetricRegistry.class).toInstance(metricRegistry);
  }
}
