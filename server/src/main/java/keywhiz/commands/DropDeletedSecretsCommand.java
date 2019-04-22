package keywhiz.commands;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.io.Console;
import java.util.Scanner;
import javax.inject.Inject;
import keywhiz.KeywhizConfig;
import keywhiz.service.daos.SecretDAO;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.joda.time.DateTime;

import static java.lang.String.format;

/**
 * Secrets which are deleted from Keywhiz--whether through the admin client or the automation
 * interface--are not actually removed from Keywhiz' database.  Instead, the link between the
 * "secret series" and "secret contents" tables is broken, but it is still possible to reconnect
 * them manually and restore a deleted secret.
 *
 * This command permanently drops information about deleted secrets from the database.  This is
 * clearly dangerous, so it should be run only when it is certain that the deleted secrets will not
 * need to be manually recovered.
 */
public class DropDeletedSecretsCommand extends ConfiguredCommand<KeywhizConfig> {
  protected static final String INPUT_DELETED_BEFORE = "deleted-before";
  protected static final String INPUT_SLEEP_MILLIS = "sleep-millis";
  private SecretDAO secretDAO;

  @Inject
  protected DropDeletedSecretsCommand(SecretDAO secretDAO) {
    super("drop-deleted-secrets", "PERMANENTLY REMOVES database records for deleted secrets");
    this.secretDAO = secretDAO;
  }

  @Override public void configure(Subparser subparser) {
    subparser.addArgument("--deleted-before")
        .dest(INPUT_DELETED_BEFORE)
        .type(String.class)
        .required(true)
        .help(
            "secrets deleted before this date will be PERMANENTLY REMOVED.  Format: 2006-01-02T15:04:05Z");

    subparser.addArgument("--sleep-millis")
        .dest(INPUT_DELETED_BEFORE)
        .type(Integer.class)
        .setDefault(500)
        .help("how many milliseconds to sleep between batches of removals");
  }

  @Override public void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) {
    // validate the input
    String deletedBeforeStr = namespace.getString(INPUT_DELETED_BEFORE);
    DateTime deletedBefore = getDateIfValid(deletedBeforeStr);
    if (deletedBefore == null) {
      // The error has already been printed
      return;
    }

    Integer sleepMillis = namespace.getInt(INPUT_SLEEP_MILLIS);
    if (sleepMillis < 0) {
      System.out.format("Milliseconds to sleep must be nonnegative; got %d", sleepMillis);
      return;
    }

    // determine how many secrets would be affected and get user confirmation
    long affectedCount = secretDAO.countSecretsDeletedBeforeDate(deletedBefore);

    System.out.format(
        "WARNING: This will PERMANENTLY remove all secrets deleted before %s.  %d secrets will be removed.  Confirm? (y/n)\n",
        deletedBefore.toString(), affectedCount);

    String confirm;
    if (System.console() != null) {
      Console console = System.console();
      confirm = console.readLine();
    } else {
      // In test environments, system.console may not be set; fall back to System.in.
      Scanner scanner = new Scanner(System.in);
      confirm = scanner.nextLine();
    }
    if (confirm == null || !(confirm.equalsIgnoreCase("y") || confirm.equalsIgnoreCase("yes"))) {
      System.out.format("Received `%s` which is not 'y'/'yes'; not removing secrets.\n", confirm);
      return;
    }

    // remove the secrets
    System.out.format("Removing %d secrets which were deleted before %s from the database\n",
        affectedCount, deletedBefore.toString());
    try {
      secretDAO.dangerPermanentlyRemoveSecretsDeletedBeforeDate(deletedBefore, sleepMillis);
    } catch (InterruptedException e) {
      System.out.println("Error removing secrets; please retry command");
      e.printStackTrace();
    }
  }

  private DateTime getDateIfValid(String deletedBeforeStr) {
    // the date string must be specified
    if (deletedBeforeStr == null || deletedBeforeStr.isEmpty()) {
      System.out.println(
          "Empty input for deleted-before; must specify a valid date in the format 2006-01-02T15:04:05Z");
      return null;
    }

    // the date must parse
    DateTime before;
    try {
      before = new DateTime(deletedBeforeStr);
    } catch (Exception e) {
      System.out.println(
          "Invalid input for deleted-before; must specify a valid date in the format 2006-01-02T15:04:05Z.  Parsing threw exception.");
      e.printStackTrace();
      return null;
    }

    // the date must be in the past
    if (before.isAfterNow() || before.isEqualNow()) {
      System.out.println(
          format("Cutoff date for deletion must be before current time; input of %s was invalid",
              before.toString()));
      return null;
    }

    return before;
  }
}
