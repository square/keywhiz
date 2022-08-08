package keywhiz.service.permissions;

import keywhiz.api.model.Client;
import keywhiz.api.model.Secret;

public class AllowClientSecretCreationPermissionCheck implements PermissionCheck {
  @Override
  public boolean isAllowed(Object source, String action, Object target) {
    return false;
  }

  @Override
  public boolean isAllowedForTargetType(Object source, String action, Class<?> targetType) {
    return
        Client.class.isAssignableFrom(source.getClass()) &&
        Action.CREATE.equals(action) &&
        Secret.class.isAssignableFrom(targetType);
  }
}
