package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import javax.inject.Inject;
import keywhiz.api.model.Client;
import keywhiz.api.model.Secret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllowClientSecretCreationPermissionCheck implements PermissionCheck {
  private static final Logger logger = LoggerFactory.getLogger(AllowClientSecretCreationPermissionCheck.class);

  private static final String SUCCESS_METRIC_NAME = MetricRegistry.name(AllowClientSecretCreationPermissionCheck.class, "success", "histogram");
  private static final String FAILURE_METRIC_NAME = MetricRegistry.name(AllowClientSecretCreationPermissionCheck.class, "failure", "histogram");

  private final MetricRegistry metricRegistry;

  @Inject
  public AllowClientSecretCreationPermissionCheck(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  @Override
  public boolean isAllowed(Object source, String action, Object target) {
    final boolean hasPermission = false;

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    emitHistogramMetrics(hasPermission);

    return hasPermission;
  }

  @Override
  public boolean isAllowedForTargetType(Object source, String action, Class<?> targetType) {
    boolean hasPermission =
        Client.class.isAssignableFrom(source.getClass()) &&
        Action.CREATE.equals(action) &&
        Secret.class.isAssignableFrom(targetType);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowedForTargetType Actor: %s, Action: %s, Target type: %s, Result: %s", source, action, targetType,
            hasPermission));

    return hasPermission;
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    metricRegistry.histogram(SUCCESS_METRIC_NAME).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    metricRegistry.histogram(FAILURE_METRIC_NAME).update(hasPermissionFailureMetricInt);
  }
}
