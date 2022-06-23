package keywhiz.service.permissions;

public interface PermissionCheck {
  boolean isAllowed(Object source, String action, Object target);

  default void checkAllowedOrThrow(Object source, String action, Object target){
    if (!isAllowed(source, action, target)) {
      throw new RuntimeException(String.format("Actor: %s, Action: %s, Target: %s, Result: %s throws exception", source, action, target,
          false));
    }
  }
}
