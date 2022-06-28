package keywhiz.service.permissions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnyPermissionCheck implements PermissionCheck {

  private static final Logger logger = LoggerFactory.getLogger(AnyPermissionCheck.class);

  private List<PermissionCheck> subordinateChecks;

  public AnyPermissionCheck(List<PermissionCheck> subordinateChecks) {
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

    return hasPermission;
  }
}
