package keywhiz.inject;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Guice;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import keywhiz.test.ServiceContext;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class ContextModuleTest {
  @Test
  public void injectsContext() {
    class Holder {
      @Inject KeywhizConfig keywhizConfig;
      @Inject Configuration config;
      @Inject Environment environment;
      @Inject MetricRegistry metricRegistry1;
      @Inject MetricRegistry metricRegistry2;
    }

    Holder holder = new Holder();

    ServiceContext context = ServiceContext.create();
    Guice
        .createInjector(new ContextModule(context.getConfig(), context.getEnvironment()))
        .injectMembers(holder);

    assertSame(context.getConfig(), holder.keywhizConfig);
    assertSame(context.getConfig(), holder.config);
    assertSame(context.getEnvironment(), holder.environment);
    assertSame(context.getMetricRegistry(), holder.metricRegistry1);
    assertSame(context.getMetricRegistry(), holder.metricRegistry2);
    assertSame(holder.metricRegistry1, holder.metricRegistry2);
  }
}
