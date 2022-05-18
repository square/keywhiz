package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Counted;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlwaysAllowPermissionCheck implements PermissionCheck{
  private static final Logger logger = LoggerFactory.getLogger(AlwaysAllowPermissionCheck.class);
  private PermissionCheck delegate;
  private MetricRegistry metricRegistry;

  @Inject
  public AlwaysAllowPermissionCheck(MetricRegistry metricRegistry, PermissionCheck delegate) {
    this.delegate = delegate;
    this.metricRegistry = metricRegistry;
  }

  @Counted
  public boolean isAllowed(KeywhizPrincipal source, String action, Object target) {
    boolean hasPermission = this.delegate.isAllowed(source, action, target);
    if (!hasPermission) {
      denied();
    }
    String logInfo = source + " wants to " + action + " on " + target;
    if (hasPermission) {
      logger.info(String.format("%s wants to %s %s returns true", source, action, target));
    } else {
      logger.info(String.format("%s wants to %s %s returns false", source, action, target));
    }
    return true;
  }

  @Counted
  private void denied() {}

  public void checkAllowedOrThrow(KeywhizPrincipal source, String action, Object target) {
    this.delegate.isAllowed(source, action, target);
  }
}
