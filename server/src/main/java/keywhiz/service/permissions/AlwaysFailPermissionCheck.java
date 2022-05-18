package keywhiz.service.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlwaysFailPermissionCheck implements PermissionCheck {
  private static final Logger logger = LoggerFactory.getLogger(AlwaysFailPermissionCheck.class);

  public AlwaysFailPermissionCheck() {}

  public boolean isAllowed(KeywhizPrincipal source, String action, Object target) {
    return false;
  }

  public void checkAllowedOrThrow(KeywhizPrincipal source, String action, Object target) {}
}
