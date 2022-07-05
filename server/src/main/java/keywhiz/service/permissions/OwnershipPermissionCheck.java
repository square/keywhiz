package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import java.util.Set;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.daos.AclDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OwnershipPermissionCheck implements PermissionCheck{
  private static final Logger logger = LoggerFactory.getLogger(OwnershipPermissionCheck.class);

  private static final String SUCCESS_METRIC_NAME = MetricRegistry.name(OwnershipPermissionCheck.class, "success", "histogram");
  private static final String FAILURE_METRIC_NAME = MetricRegistry.name(OwnershipPermissionCheck.class, "failure", "histogram");

  private final MetricRegistry metricRegistry;
  private final AclDAO aclDAO;

  @Inject
  public OwnershipPermissionCheck(MetricRegistry metricRegistry, AclDAO.AclDAOFactory aclDAOFactory) {
    this.metricRegistry = metricRegistry;
    this.aclDAO = aclDAOFactory.readwrite();
  }

  public boolean isAllowed(Object source, String action, Object target) {
    boolean hasPermission = false;

    if (isClient(source)) {
      Set<Group> clientGroups = aclDAO.getGroupsFor((Client) source);

      String secretOwner = null;
      if (target instanceof Secret) {
        secretOwner = ((Secret) target).getOwner();
      } else if (target instanceof SecretSeries) {
        secretOwner = ((SecretSeries) target).owner();
      } else if (target instanceof SecretSeriesAndContent) {
        secretOwner = ((SecretSeriesAndContent) target).series().owner();
      }

      for (Group group : clientGroups) {
        if (group.getName().equals(secretOwner)) {
          hasPermission = true;
          break;
        }
      }
    }

    emitHistogramMetrics(hasPermission);

    logger.info(
        String.format("isAllowed Actor: %s, Action: %s, Target: %s, Result: %s", source, action, target,
            hasPermission));

    return hasPermission;
  }

  private boolean isClient(Object source) {
    return source instanceof Client;
  }

  private void emitHistogramMetrics(Boolean isPermitted) {
    int hasPermissionSuccessMetricInt = isPermitted ? 1 : 0;
    metricRegistry.histogram(SUCCESS_METRIC_NAME).update(hasPermissionSuccessMetricInt);

    int hasPermissionFailureMetricInt = isPermitted ? 0 : 1;
    metricRegistry.histogram(FAILURE_METRIC_NAME).update(hasPermissionFailureMetricInt);
  }
}
