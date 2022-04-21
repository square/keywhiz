package keywhiz.service.permissions;

public interface PermissionCheck {
  boolean isAllowed(KeywhizPrincipal source, String action, Object target);
  void checkAllowedOrThrow(KeywhizPrincipal source, String action, Object target);
}
