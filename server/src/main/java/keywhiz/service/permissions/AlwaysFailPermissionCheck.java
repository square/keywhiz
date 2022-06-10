package keywhiz.service.permissions;

import javax.inject.Inject;

public class AlwaysFailPermissionCheck implements PermissionCheck {
  @Inject
  public AlwaysFailPermissionCheck() {}

  public boolean isAllowed(Object source, String action, Object target) {
    return false;
  }
}
