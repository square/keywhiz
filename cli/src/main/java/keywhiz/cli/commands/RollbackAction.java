package keywhiz.cli.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.configs.RollbackActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class RollbackAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(RollbackAction.class);

  private final RollbackActionConfig rollbackActionConfig;
  private final KeywhizClient keywhizClient;

  @VisibleForTesting
  InputStream inputStream = System.in;

  public RollbackAction(RollbackActionConfig rollbackActionConfig, KeywhizClient client) {
    this.rollbackActionConfig = rollbackActionConfig;
    this.keywhizClient = client;
  }

  @Override public void run() {
    try {
      if (rollbackActionConfig.name == null || !validName(rollbackActionConfig.name)) {
        throw new IllegalArgumentException(
            format("Invalid name, must match %s", VALID_NAME_PATTERN));
      }

      if (rollbackActionConfig.id == null) {
        throw new IllegalArgumentException(
            "Version ID must be specified for rollback.  List the secret's versions to view IDs.");
      }

      SanitizedSecret sanitizedSecret =
          keywhizClient.getSanitizedSecretByName(rollbackActionConfig.name);

      // Get user confirmation for the rollback
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
      while (true) {
        System.out.println(
            format("Please confirm rollback of secret '%s' to version with ID %d: Y/N",
                sanitizedSecret.name(), rollbackActionConfig.id));
        String line = reader.readLine();

        if (line == null /* EOF */ || line.toUpperCase().startsWith("N")) {
          return;
        } else if (line.toUpperCase().startsWith("Y")) {
          logger.info("Rolling back secret '{}' to version {}", sanitizedSecret.name(),
              rollbackActionConfig.id);
          keywhizClient.rollbackSecret(sanitizedSecret.name(), rollbackActionConfig.id);
          return;
        } // else loop again
      }
    } catch (NotFoundException e) {
      throw new AssertionError("Secret does not exist: " + rollbackActionConfig.name);
    } catch (IOException e) {
      throw new AssertionError(String.format(
          "Error executing rollback; check whether ID %d is a valid version ID for secret %s by listing the secret's versions%nError: %s",
          rollbackActionConfig.id, rollbackActionConfig.name, e.getMessage()));
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}
