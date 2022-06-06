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

    int hasPermissionSuccessMetricInt = hasPermission ? 1 : 0;
    String successMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "isAllowed", "success", "histogram");
    metricRegistry.histogram(successMetricName).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = hasPermission ? 0 : 1;
    String failureMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "isAllowed", "failure", "histogram");
    metricRegistry.histogram(failureMetricName).update(hasPermissionFailureMetricInt);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return true;
  }

  @Override
  public void checkAllowedOrThrow(KeywhizPrincipal source, String action, Object target) {
    String successMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "checkAllowedOrThrow", "success", "histogram");
    String exceptionMetricName = MetricRegistry.name(AlwaysAllowDelegatingPermissionCheck.class, "checkAllowedOrThrow", "exception", "histogram");
    try {
      delegate.checkAllowedOrThrow(source, action, target);

      metricRegistry.histogram(successMetricName).update(1);
      metricRegistry.histogram(exceptionMetricName).update(0);
    } catch (RuntimeException e) {
      metricRegistry.histogram(successMetricName).update(0);
      metricRegistry.histogram(exceptionMetricName).update(1);

      logger.error(String.format("checkAllowedOrThrow Actor: %s, Action: %s, Target: %s throws exception", source, action, target),e);
    }
  }
}
