package keywhiz.cli.commands;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.List;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.cli.Printing;
import keywhiz.cli.configs.ListVersionsActionConfig;
import keywhiz.client.KeywhizClient;
import keywhiz.client.KeywhizClient.NotFoundException;

import static java.lang.String.format;
import static keywhiz.cli.Utilities.VALID_NAME_PATTERN;
import static keywhiz.cli.Utilities.validName;

public class ListVersionsAction implements Runnable {

  private final ListVersionsActionConfig listVersionsActionConfig;
  private final KeywhizClient keywhizClient;
  private final Printing printing;

  public ListVersionsAction(ListVersionsActionConfig listVersionsActionConfig, KeywhizClient client, Printing printing) {
    this.listVersionsActionConfig = listVersionsActionConfig;
    this.keywhizClient = client;
    this.printing = printing;
  }

  @Override public void run() {
    if (listVersionsActionConfig.name == null || !validName(listVersionsActionConfig.name)) {
      throw new IllegalArgumentException(format("Invalid name, must match %s", VALID_NAME_PATTERN));
    }

    try {
      SanitizedSecret sanitizedSecret =
          keywhizClient.getSanitizedSecretByName(listVersionsActionConfig.name);

      List<SanitizedSecret> versions =
          keywhizClient.listSecretVersions(sanitizedSecret.name(),
              listVersionsActionConfig.idx, listVersionsActionConfig.number);
      // The current version can never be negative
      printing.printSecretVersions(versions, sanitizedSecret.version().orElse(-1L));
    } catch (NotFoundException e) {
      throw new AssertionError("Secret does not exist: " + listVersionsActionConfig.name);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
