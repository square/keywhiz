package keywhiz.cli.commands;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.RenameActionConfig;
import keywhiz.client.KeywhizClient;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class RenameActionTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock KeywhizClient keywhiz;

  @Test(expected = IllegalArgumentException.class)
  public void rejectsSecretIdAndSecretNameBothMissing() {
    RenameActionConfig config = new RenameActionConfig();
    config.secretId = null;
    config.secretName = null;

    RenameAction action = new RenameAction(config, keywhiz);
    action.run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsSecretIdAndSecretNameBothPresent() {
    RenameActionConfig config = new RenameActionConfig();
    config.secretId = 1L;
    config.secretName = "foo";

    RenameAction action = new RenameAction(config, keywhiz);
    action.run();
  }

  @Test
  public void renamesSecretUsingSecretId() throws IOException {
    Long secretId = 1L;
    String newName = "bar";

    RenameActionConfig config = new RenameActionConfig();
    config.secretId = secretId;
    config.newName = newName;

    RenameAction action = new RenameAction(config, keywhiz);
    action.run();

    verify(keywhiz).renameSecret(secretId, newName);
  }

  @Test
  public void renamesSecretUsingSecretName() throws IOException {
    Long secretId = 1L;
    String secretName = "foo";
    String newName = "bar";

    SanitizedSecret secret = SanitizedSecret.of(secretId, secretName);

    when(keywhiz.getSanitizedSecretByName(secretName)).thenReturn(secret);

    RenameActionConfig config = new RenameActionConfig();
    config.secretName = secretName;
    config.newName = newName;

    RenameAction action = new RenameAction(config, keywhiz);
    action.run();

    verify(keywhiz).renameSecret(secretId, newName);
  }
}
