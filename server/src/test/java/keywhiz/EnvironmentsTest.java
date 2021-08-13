package keywhiz;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class EnvironmentsTest {
  @Test
  public void createsEnvironmentFromBootstrap() {
    KeywhizService keywhiz = new KeywhizService();
    Bootstrap<KeywhizConfig> bootstrap = new Bootstrap<>(keywhiz);
    Environment environment = Environments.fromBootstrap(bootstrap);

    assertEquals(keywhiz.getName(), environment.getName());
    assertNotSame(bootstrap.getObjectMapper(), environment.getObjectMapper());
    assertNotNull(environment.getValidator());
    assertSame(bootstrap.getMetricRegistry(), environment.metrics());
    assertSame(bootstrap.getClassLoader(), environment.getAdminContext().getClassLoader());
  }
}
