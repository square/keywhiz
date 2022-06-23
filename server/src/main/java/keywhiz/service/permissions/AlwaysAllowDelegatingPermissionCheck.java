package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlwaysAllowDelegatingPermissionCheck implements PermissionCheck {
  private static final Logger logger = LoggerFactory.getLogger(AlwaysAllowDelegatingPermissionCheck.class);
  private static final String successMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "success", "histogram");
  private static final String failureMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "failure", "histogram");

  private PermissionCheck delegate;
  private MetricRegistry metricRegistry;

  @Inject
  public AlwaysAllowDelegatingPermissionCheck(MetricRegistry metricRegistry, PermissionCheck delegate) {
    this.delegate = delegate;
    this.metricRegistry = metricRegistry;
  }

  public boolean isAllowed(Object source, String action, Object target) {
    boolean hasPermission = delegate.isAllowed(source, action, target);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return true;
  }

  @Override
  public void checkAllowedOrThrow(Object source, String action, Object target) {
    Boolean isPermitted;
    try {
      delegate.checkAllowedOrThrow(source, action, target);
      isPermitted = true;
    } catch (RuntimeException e) {
      logger.error(String.format("checkAllowedOrThrow Actor: %s, Action: %s, Target: %s throws exception", source, action, target),e);
      isPermitted = false;
    }

    emitHistogramMetrics(isPermitted);
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    metricRegistry.histogram(successMetricName).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    metricRegistry.histogram(failureMetricName).update(hasPermissionFailureMetricInt);
  }
}
