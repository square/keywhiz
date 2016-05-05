package keywhiz.service.resources;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import java.util.TreeMap;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatusResponseTest {
  HealthCheckRegistry registry;
  Environment environment;
  StatusResource status;

  @Before
  public void setUp() throws Exception {
    this.registry = mock(HealthCheckRegistry.class);
    this.environment = mock(Environment.class);
    this.status = new StatusResource(environment);

    when(environment.healthChecks()).thenReturn(registry);
  }

  @Test
  public void testStatusOk() throws Exception {
    when(registry.runHealthChecks()).thenReturn(new TreeMap<>());
    Response r = status.get();
    assertThat(r.getStatus()).isEqualTo(200);
  }

  @Test
  public void testStatusWarn() throws Exception {
    TreeMap<String, HealthCheck.Result> map = new TreeMap<>();
    map.put("test", HealthCheck.Result.unhealthy("failing"));

    when(registry.runHealthChecks()).thenReturn(map);
    Response r = status.get();
    assertThat(r.getStatus()).isEqualTo(500);
  }
}