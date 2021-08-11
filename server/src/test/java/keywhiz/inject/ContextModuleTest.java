package keywhiz.inject;

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
    }

    Holder holder = new Holder();

    ServiceContext context = ServiceContext.create();
    Guice
        .createInjector(new ContextModule(context.getConfig(), context.getEnvironment()))
        .injectMembers(holder);

    assertSame(context.getConfig(), holder.keywhizConfig);
    assertSame(context.getConfig(), holder.config);
    assertSame(context.getEnvironment(), holder.environment);
  }
}
