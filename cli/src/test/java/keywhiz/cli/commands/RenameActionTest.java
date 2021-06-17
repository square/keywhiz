package keywhiz.cli.commands;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.RenameActionConfig;
import keywhiz.client.KeywhizClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RenameActionTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock KeywhizClient keywhiz;

  @Test
  public void rejectsNonSecretResourceType() {
    RenameActionConfig config = new RenameActionConfig();
    config.resourceType = "client";

    RenameAction action = new RenameAction(config, keywhiz);
    thrown.expect(IllegalArgumentException.class);
    action.run();
  }

  @Test
  public void renamesSecret() throws IOException {
    Long secretId = 1L;
    String secretName = "foo";
    String newName = "bar";

    SanitizedSecret secret = SanitizedSecret.of(secretId, secretName);

    when(keywhiz.getSanitizedSecretByName(secretName)).thenReturn(secret);

    RenameActionConfig config = new RenameActionConfig();
    config.resourceType = "secret";
    config.oldName = secretName;
    config.newName = newName;

    RenameAction action = new RenameAction(config, keywhiz);
    action.run();

    verify(keywhiz).renameSecret(secretId, newName);
  }
}
