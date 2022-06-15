package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import io.dropwizard.auth.Auth;
import keywhiz.api.model.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomationClientPermissionCheck implements PermissionCheck{
  private static final Logger logger = LoggerFactory.getLogger(
      AutomationClientPermissionCheck.class);

  private MetricRegistry metricRegistry;

  @Inject
  public AutomationClientPermissionCheck(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  public boolean isAllowed(KeywhizPrincipal source, String action, Object target) {
    boolean hasPermission = isAutomation(source);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return hasPermission;
  }

  private boolean isAutomation(KeywhizPrincipal source) {
    if (source instanceof Client) {
      Client client = (Client) source;
      return client.isAutomationAllowed();
    }

    return false;
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    String successMetricName = MetricRegistry.name(AutomationClientPermissionCheck.class, "success", "histogram");
    metricRegistry.histogram(successMetricName).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    String failureMetricName = MetricRegistry.name(AutomationClientPermissionCheck.class, "failure", "histogram");
    metricRegistry.histogram(failureMetricName).update(hasPermissionFailureMetricInt);
  }
}
