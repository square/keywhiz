package keywhiz.service.permissions;

import javax.annotation.Nullable;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;

public abstract class KeywhizPrincipalImpl extends Client implements KeywhizPrincipal{
  public KeywhizPrincipalImpl(long id, String name, @Nullable String description,
      @Nullable String spiffeId, ApiDate createdAt,
      @Nullable String createdBy, ApiDate updatedAt,
      @Nullable String updatedBy,
      @Nullable ApiDate lastSeen,
      @Nullable ApiDate expiration, boolean enabled,
      boolean automationAllowed) {
    super(id, name, description, spiffeId, createdAt, createdBy, updatedAt, updatedBy, lastSeen,
        expiration, enabled, automationAllowed);
  }
}
