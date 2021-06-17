package keywhiz.cli.commands;

import java.io.IOException;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.RenameActionConfig;
import keywhiz.client.KeywhizClient;

public class RenameAction implements Runnable {

  private final RenameActionConfig config;
  private final KeywhizClient keywhiz;

  public RenameAction(RenameActionConfig config, KeywhizClient keywhiz) {
    this.config = config;
    this.keywhiz = keywhiz;
  }

  @Override public void run() {
    switch (config.resourceType) {
      case "secret":
        renameSecret();
        break;
      default:
        throw new IllegalArgumentException(
            String.format(
                "Unsupported resource type %s. Only the secret resource type can be renamed.",
                config.resourceType));
    }
  }

  private void renameSecret() {
    try {
      SanitizedSecret secret = keywhiz.getSanitizedSecretByName(config.oldName);
      keywhiz.renameSecret(secret.id(), config.newName);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
