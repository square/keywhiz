package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlwaysAllowDelegatingPermissionCheck implements PermissionCheck {
  private static final Logger logger = LoggerFactory.getLogger(AlwaysAllowDelegatingPermissionCheck.class);

  private PermissionCheck delegate;
  private MetricRegistry metricRegistry;

  @Inject
  public AlwaysAllowDelegatingPermissionCheck(MetricRegistry metricRegistry, PermissionCheck delegate) {
    this.delegate = delegate;
    this.metricRegistry = metricRegistry;
  }

  public boolean isAllowed(KeywhizPrincipal source, String action, Object target) {
    boolean hasPermission = delegate.isAllowed(source, action, target);

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return true;
  }

  @Override
  public void checkAllowedOrThrow(KeywhizPrincipal source, String action, Object target) {
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
    String successMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "success", "histogram");
    metricRegistry.histogram(successMetricName).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    String failureMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "failure", "histogram");
    metricRegistry.histogram(failureMetricName).update(hasPermissionFailureMetricInt);
  }
}
