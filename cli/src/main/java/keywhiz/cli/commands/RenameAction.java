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
    validate();

    try {
      if (config.secretName != null) {
          SanitizedSecret secret = keywhiz.getSanitizedSecretByName(config.secretName);
          keywhiz.renameSecret(secret.id(), config.newName);
      } else if (config.secretId != null) {
        keywhiz.renameSecret(config.secretId, config.newName);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void validate() {
    boolean namePresent = config.secretName != null;
    boolean idPresent = config.secretId != null;
    boolean onlyOneOfNameOrIdPresent = namePresent ^ idPresent;
    if (!onlyOneOfNameOrIdPresent) {
      throw new IllegalArgumentException("Must specify either the name or ID of the secret to rename (but not both)");
    }
  }
}
