package keywhiz.service.permissions;

public interface PermissionCheck {
  boolean isAllowed(Object source, String action, Object target);

  boolean isAllowedForTargetType(Object source, String action, Class<?> targetType);

  default void checkAllowedOrThrow(Object source, String action, Object target){
    if (!isAllowed(source, action, target)) {
      throw new RuntimeException(String.format("Actor: %s, Action: %s, Target: %s, Result: %s throws exception", source, action, target,
          false));
    }
  }

  default void checkAllowedForTargetTypeOrThrow(Object source, String action, Class<?> targetType) {
    if (!isAllowedForTargetType(source, action, targetType)) {
      throw new RuntimeException(String.format("Actor: %s, Action: %s, Target type: %s, Result: %s throws exception", source, action, targetType,
          false));
    }
  }
}
