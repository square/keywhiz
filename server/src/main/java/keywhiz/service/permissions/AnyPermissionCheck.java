package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnyPermissionCheck implements PermissionCheck {

  private static final Logger logger = LoggerFactory.getLogger(AnyPermissionCheck.class);

  private static final String SUCCESS_METRIC_NAME = MetricRegistry.name(AnyPermissionCheck.class, "success", "histogram");
  private static final String FAILURE_METRIC_NAME = MetricRegistry.name(AnyPermissionCheck.class, "failure", "histogram");

  private final List<PermissionCheck> subordinateChecks;
  private final MetricRegistry metricRegistry;

  public AnyPermissionCheck(MetricRegistry metricRegistry, List<PermissionCheck> subordinateChecks) {
    this.metricRegistry = metricRegistry;
    this.subordinateChecks = ImmutableList.copyOf(subordinateChecks);
  }

  public boolean isAllowed(Object source, String action, Object target) {
    boolean hasPermission = false;

    for (PermissionCheck subordinateCheck : subordinateChecks) {
      if (subordinateCheck.isAllowed(source, action, target)) {
        hasPermission = true;
        break;
      }
    }

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    emitHistogramMetrics(hasPermission);

    return hasPermission;
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    metricRegistry.histogram(SUCCESS_METRIC_NAME).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    metricRegistry.histogram(FAILURE_METRIC_NAME).update(hasPermissionFailureMetricInt);
  }
}
