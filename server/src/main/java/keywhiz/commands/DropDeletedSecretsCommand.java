package keywhiz.commands;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import java.io.Console;
import java.sql.SQLException;
import java.util.Scanner;
import javax.sql.DataSource;
import keywhiz.KeywhizConfig;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.service.daos.SecretContentMapper;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import keywhiz.service.daos.SecretSeriesMapper;
import keywhiz.utility.DSLContexts;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.joda.time.DateTime;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger logger = LoggerFactory.getLogger(DropDeletedSecretsCommand.class);
  protected static final String INPUT_DELETED_BEFORE = "deleted-before";
  protected static final String INPUT_SLEEP_MILLIS = "sleep-millis";

  private SecretDAO secretDAO;

  public DropDeletedSecretsCommand() {
    super("drop-deleted-secrets", "PERMANENTLY REMOVES database records for deleted secrets");
  }

  @VisibleForTesting
  public DropDeletedSecretsCommand(SecretDAO secretDAO) {
    super("drop-deleted-secrets", "PERMANENTLY REMOVES database records for deleted secrets");
    this.secretDAO = secretDAO;
  }

  @Override public void configure(Subparser subparser) {
    // Necessary to retain the positional config-file argument
    super.configure(subparser);

    subparser.addArgument("--deleted-before")
        .dest(INPUT_DELETED_BEFORE)
        .type(String.class)
        .required(true)
        .help(
            "secrets deleted before this date will be PERMANENTLY REMOVED.  Format: 2006-01-02T15:04:05Z");

    subparser.addArgument("--sleep-millis")
        .dest(INPUT_SLEEP_MILLIS)
        .type(Integer.class)
        .setDefault(500)
        .help("how many milliseconds to sleep between batches of removals");
  }

  @Override public void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {
    if (secretDAO == null) {
      secretDAO = getSecretDAO(bootstrap, config);
    }

    // validate the input
    String deletedBeforeStr = namespace.getString(INPUT_DELETED_BEFORE);
    DateTime deletedBefore = getDateIfValid(deletedBeforeStr);
    if (deletedBefore == null) {
      // The error has already been printed
      return;
    }

    Integer sleepMillis = namespace.getInt(INPUT_SLEEP_MILLIS);
    if (sleepMillis < 0) {
      System.out.format("Milliseconds to sleep must be nonnegative; got %d\n", sleepMillis);
      return;
    }

    // determine how many secrets would be affected and get user confirmation
    long totalDeletedCount = secretDAO.countDeletedSecrets();
    long affectedCount = secretDAO.countSecretsDeletedBeforeDate(deletedBefore);

    if (affectedCount == 0) {
      System.out.format(
          "No secrets deleted before %s were found (out of %d deleted secrets); not altering the database.\n",
          deletedBefore.toString(), totalDeletedCount);

      logger.info(format(
          "drop-deleted-secrets: No secrets deleted before %s were found (out of %d deleted secrets); not altering the database.",
          deletedBefore.toString(), totalDeletedCount));
      return;
    }

    System.out.format(
        "WARNING: This will PERMANENTLY remove all secrets deleted before %s.  "
            + "%d secrets will be removed, out of %d deleted secrets.  Confirm? (y/n)\n",
        deletedBefore.toString(), affectedCount, totalDeletedCount);

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

    logger.info(format(
        "drop-deleted-secrets: Removing %d secrets which were deleted before %s from the database",
        affectedCount, deletedBefore.toString()));
    try {
      secretDAO.dangerPermanentlyRemoveSecretsDeletedBeforeDate(deletedBefore, sleepMillis);
    } catch (InterruptedException e) {
      System.out.println("Error removing secrets; please retry command");
      e.printStackTrace();

      logger.info(format(
          "drop-deleted-secrets: error removing secrets (some secrets may already be deleted): %s",
          e.getMessage()));
    }
  }

  private SecretDAO getSecretDAO(Bootstrap<KeywhizConfig> bootstrap, KeywhizConfig config)
      throws SQLException {
    DataSource dataSource = config.getDataSourceFactory()
        .build(new MetricRegistry(), "drop-deleted-secrets-datasource");
    DSLContext dslContext = DSLContexts.databaseAgnostic(dataSource);

    SecretContentDAOFactory secretContentDAOFactory = new SecretContentDAOFactory(
        dslContext,
        dslContext,
        bootstrap.getObjectMapper(),
        new SecretContentMapper(bootstrap.getObjectMapper()),
        null,
        null
    );

    SecretSeriesDAOFactory secretSeriesDAOFactory = new SecretSeriesDAOFactory(
        dslContext,
        dslContext,
        bootstrap.getObjectMapper(),
        new SecretSeriesMapper(bootstrap.getObjectMapper()),
        null
    );

    return new SecretDAO(
        dslContext,
        secretContentDAOFactory,
        secretSeriesDAOFactory,
        null
    );
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
      System.out.format(
          "Cutoff date for deletion must be before current time; input of %s was invalid.\n",
          before.toString());
      return null;
    }

    return before;
  }
}
