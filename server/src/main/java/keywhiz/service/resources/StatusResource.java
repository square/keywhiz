package keywhiz.service.resources;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.setup.Environment;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

/** Serve status information */
@Path("/_status")
@Produces(APPLICATION_JSON)
public class StatusResource {
  private Environment environment;
  private static final Logger logger = LoggerFactory.getLogger(SecretDeliveryResource.class);

  @Inject public StatusResource(Environment environment) {
    this.environment = environment;
  }

  private static class StatusResponse {
    public String status;
    public String message;
    public SortedMap<String, HealthCheck.Result> results;

    StatusResponse(String status, String message, SortedMap<String, HealthCheck.Result> results) {
      this.status = status;
      this.message = message;
      this.results = results;
    }
  }

  @Timed @ExceptionMetered
  @GET
  public Response get() {
    HealthCheckRegistry checks = this.environment.healthChecks();
    SortedMap<String, HealthCheck.Result> results = checks.runHealthChecks();

    List<String> failing = results.entrySet().stream()
        .filter(r -> !r.getValue().isHealthy())
        .map(Map.Entry::getKey)
        .collect(toList());

    if (!failing.isEmpty()) {
      logger.warn("Health checks failed: {}", results);
      String message = "failing health checks: " + Arrays.toString(failing.toArray());
      StatusResponse sr = new StatusResponse("critical", message, results);
      return Response.serverError().entity(sr).build();
    }
    StatusResponse sr = new StatusResponse("ok", "ok", results);
    return Response.ok(sr).build();
  }
}
