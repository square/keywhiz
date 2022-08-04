package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlwaysAllowDelegatingPermissionCheck implements PermissionCheck {
  private static final Logger logger = LoggerFactory.getLogger(AlwaysAllowDelegatingPermissionCheck.class);

  private static final String SUCCESS_METRIC_NAME = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "success", "histogram");
  private static final String FAILURE_METRIC_NAME = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "failure", "histogram");

  private final PermissionCheck delegate;
  private final MetricRegistry metricRegistry;

  @Inject
  public AlwaysAllowDelegatingPermissionCheck(MetricRegistry metricRegistry, PermissionCheck delegate) {
    this.delegate = delegate;
    this.metricRegistry = metricRegistry;
  }

  @Override
  public boolean isAllowed(Object source, String action, Object target) {
    boolean hasPermission = delegate.isAllowed(source, action, target);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return true;
  }

  @Override
  public boolean isAllowedForTargetType(Object source, String action, Class<?> targetType) {
    boolean hasPermission = delegate.isAllowedForTargetType(source, action, targetType);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowedByType Actor: %s, Action: %s, Target type: %s, Result: %s", source, action, targetType,
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
      logger.error(String.format("checkAllowedOrThrow Actor: %s, Action: %s, Target: %s throws exception", source, action, target), e);
      isPermitted = false;
    }

    emitHistogramMetrics(isPermitted);
  }

  @Override
  public void checkAllowedForTargetTypeOrThrow(Object source, String action, Class<?> targetType) {
    Boolean isPermitted;
    try {
      delegate.checkAllowedForTargetTypeOrThrow(source, action, targetType);
      isPermitted = true;
    } catch (RuntimeException e) {
      logger.error(String.format("checkAllowedByTypeOrThrow Actor: %s, Action: %s, Target type: %s throws exception", source, action, targetType), e);
      isPermitted = false;
    }

    emitHistogramMetrics(isPermitted);
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    metricRegistry.histogram(SUCCESS_METRIC_NAME).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    metricRegistry.histogram(FAILURE_METRIC_NAME).update(hasPermissionFailureMetricInt);
  }
}
