package keywhiz.service.resources;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/** Serve status information */
@Path("/_status")
@Produces(APPLICATION_JSON)
public class StatusResource {
  private Environment environment;

  @Inject public StatusResource(Environment environment) {
    this.environment = environment;
  }

  @GET
  public boolean get() {
    HealthCheckRegistry checks = this.environment.healthChecks();
    SortedMap<String, HealthCheck.Result> results = checks.runHealthChecks();

    List<String> failing = results.entrySet().stream()
        .filter(r -> !r.getValue().isHealthy())
        .map(Map.Entry::getKey)
        .collect(toList());

    if (!failing.isEmpty()) {
      throw new InternalServerErrorException(
          "failing health checks: " + Arrays.toString(failing.toArray()));
    }

    return true;
  }
}
