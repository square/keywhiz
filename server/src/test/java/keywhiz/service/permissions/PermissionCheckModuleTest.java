package keywhiz.service.permissions;

import javax.inject.Inject;
import keywhiz.api.model.Client;
import keywhiz.api.model.Secret;
import org.junit.Test;

import static keywhiz.test.KeywhizTests.createInjector;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PermissionCheckModuleTest {
  @Test
  public void createsInjector() {
    assertNotNull(createInjector());
  }

  @Test
  public void injectPermissionCheckProvider() {
    class Holder {
      @Inject PermissionCheck permissionCheck;
    }
    Holder holder = new Holder();
    createInjector().injectMembers(holder);
    assertNotNull(holder.permissionCheck);
  }

  @Test
  public void injectedPermissionCheckAllowsClientSecretCreation() {
    PermissionCheck permissionCheck = createInjector().getInstance(PermissionCheck.class);
    assertTrue(permissionCheck.isAllowedForTargetType(newClient(), Action.CREATE, Secret.class));
  }

  private static Client newClient() {
    return new Client(
        0L,
        "name",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false);
  }

}
