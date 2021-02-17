package keywhiz.auth.mutualssl;

import java.net.URI;
import java.security.Principal;

public class SpiffePrincipal implements Principal {
  private final URI spiffeId;

  public SpiffePrincipal(URI spiffeId) {
    this.spiffeId = spiffeId;
  }

  @Override public String getName() {
    return spiffeId.toString();
  }

  public URI getSpiffeId() {
    return spiffeId;
  }

  /**
   * Use the workload id of a Spiffe Id as the client name.
   */
  public String getClientName() {
    String path = spiffeId.getPath();
    // Drop the leading '/' character.
    return path.isEmpty() ? path : path.substring(1);
  }
}
