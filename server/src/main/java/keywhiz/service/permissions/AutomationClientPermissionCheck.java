package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import keywhiz.api.model.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomationClientPermissionCheck implements PermissionCheck {
  private static final Logger logger = LoggerFactory.getLogger(AutomationClientPermissionCheck.class);

  private static final String SUCCESS_METRIC_NAME = MetricRegistry.name(AutomationClientPermissionCheck.class, "success", "histogram");
  private static final String FAILURE_METRIC_NAME = MetricRegistry.name(AutomationClientPermissionCheck.class, "failure", "histogram");

  private final MetricRegistry metricRegistry;

  @Inject
  public AutomationClientPermissionCheck(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  public boolean isAllowed(Object source, String action, Object target) {
    boolean hasPermission = isAutomation(source);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return hasPermission;
  }

  public boolean isAllowedForTargetType(Object source, String action, Class<?> targetType) {
    boolean hasPermission = isAutomation(source);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowedByType Actor: %s, Action: %s, Target type: %s, Result: %s", source, action, targetType,
            hasPermission));

    return hasPermission;
  }

  private static boolean isAutomation(Object source) {
    if (source instanceof Client) {
      Client client = (Client) source;
      return client.isAutomationAllowed();
    }

    return false;
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    metricRegistry.histogram(SUCCESS_METRIC_NAME).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    metricRegistry.histogram(FAILURE_METRIC_NAME).update(hasPermissionFailureMetricInt);
  }
}
