package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import java.util.Arrays;
import keywhiz.api.model.AutomationClient;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AllowClientSecretCreationPermissionCheckTest {

  private AllowClientSecretCreationPermissionCheck permissionCheck;

  @Before
  public void before() {
    permissionCheck = new AllowClientSecretCreationPermissionCheck(new MetricRegistry());
  }

  @Test
  public void allowsClientsToCreateSecrets() {
    assertTrue(permissionCheck.isAllowedForTargetType(newClient(), Action.CREATE, Secret.class));
  }

  @Test
  public void allowsAutomationClientsToCreateSecrets() {
    assertTrue(permissionCheck.isAllowedForTargetType(newAutomationClient(), Action.CREATE, Secret.class));
  }

  @Test
  public void doesNotAllowClientsToDeleteSecrets() {
    assertFalse(permissionCheck.isAllowedForTargetType(newClient(), Action.DELETE, Secret.class));
  }

  @Test
  public void doesNotAllowClientsToCreateOtherResources() {
    for (Class<?> clazz : Arrays.asList(AutomationClient.class, Client.class, Group.class)) {
      assertFalse(permissionCheck.isAllowedForTargetType(newClient(), Action.CREATE, clazz));
    }
  }

  @Test
  public void doesNotAllowNonClientsToCreateSecrets() {
    assertFalse(permissionCheck.isAllowed(newGroup(), Action.CREATE, Secret.class));
  }

  private static Client newClient() {
    return newClient(false);
  }

  private static Client newClient(boolean automationAllowed) {
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
        automationAllowed);
  }

  private static AutomationClient newAutomationClient() {
    return AutomationClient.of(newClient(true));
  }

  private static Group newGroup() {
    return new Group(
        0L,
        "name",
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
