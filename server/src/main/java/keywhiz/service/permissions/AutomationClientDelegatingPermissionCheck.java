package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomationClientDelegatingPermissionCheck implements PermissionCheck{
  private static final Logger logger = LoggerFactory.getLogger(
      AutomationClientDelegatingPermissionCheck.class);

  private PermissionCheck delegate;
  private MetricRegistry metricRegistry;

  @Inject
  public AutomationClientDelegatingPermissionCheck(MetricRegistry metricRegistry, PermissionCheck delegate) {
    this.delegate = delegate;
    this.metricRegistry = metricRegistry;
  }

  public boolean isAllowed(KeywhizPrincipal source, String action, Object target) {
    boolean hasPermission;

    if (isAutomation(source)) {
      hasPermission = true;
    } else {
      hasPermission = delegate.isAllowed(source, action, target);
    }

    int hasPermissionSuccessMetricInt = hasPermission ? 1 : 0;
    String successMetricName = MetricRegistry.name(AutomationClientDelegatingPermissionCheck.class, "isAllowed", "success", "histogram");
    metricRegistry.histogram(successMetricName).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = hasPermission ? 0 : 1;
    String failureMetricName = MetricRegistry.name(AutomationClientDelegatingPermissionCheck.class, "isAllowed", "failure", "histogram");
    metricRegistry.histogram(failureMetricName).update(hasPermissionFailureMetricInt);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return hasPermission;
  }

  private boolean isAutomation(KeywhizPrincipal source) {
    //TODO(violet): authenticate source as automation, return true for now

    return true;
  }

  @Override
  public void checkAllowedOrThrow(KeywhizPrincipal source, String action, Object target) {
    String successMetricName = MetricRegistry.name(AutomationClientDelegatingPermissionCheck.class, "checkAllowedOrThrow", "success", "histogram");
    String exceptionMetricName = MetricRegistry.name(AutomationClientDelegatingPermissionCheck.class, "checkAllowedOrThrow", "exception", "histogram");

    if (isAutomation(source)) {
      metricRegistry.histogram(successMetricName).update(1);
      metricRegistry.histogram(exceptionMetricName).update(0);
      logger.info(
          String.format("checkAllowedOrThrow Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
              true));
    } else {
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
}
