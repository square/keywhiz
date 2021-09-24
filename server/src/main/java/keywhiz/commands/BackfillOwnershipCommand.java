package keywhiz.commands;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Secrets.SECRETS;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.setup.Bootstrap;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import keywhiz.Environments;
import keywhiz.KeywhizConfig;
import keywhiz.inject.InjectorFactory;
import keywhiz.service.config.Readonly;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Record1;

public class BackfillOwnershipCommand extends ConfiguredCommand<KeywhizConfig> {
  private static final class Args {
    private static final String BATCH_SIZE = "batchSize";
    private static final String DELAY = "delay";
  }

  public BackfillOwnershipCommand() {
    super("backfill-ownership", "Backfills secret ownership");
  }

  @Override public void configure(Subparser subparser) {
    super.configure(subparser);

    subparser.addArgument("--batchSize")
        .dest(Args.BATCH_SIZE)
        .type(Integer.class)
        .setDefault(1)
        .help("Number of records to process in one batch");

    subparser.addArgument("--delay")
        .dest(Args.DELAY)
        .type(String.class)
        .setDefault("PT0S")
        .help("Delay between batches in ISO 8601 duration format");
  }

  @Override protected void run(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) throws Exception {
    execute(bootstrap, namespace, config);
  }

  public int execute(Bootstrap<KeywhizConfig> bootstrap, Namespace namespace,
      KeywhizConfig config) {
    ManagedDataSource dataSource = config.getDataSourceFactory().build(new MetricRegistry(), "backfill-ownership-datasource");
    Injector injector = InjectorFactory.createInjector(
        config,
        Environments.fromBootstrap(bootstrap),
        dataSource);

    DSLContext jooq = injector.getInstance(Key.get(DSLContext.class, Readonly.class));
    int batchSize = getBatchSize(namespace);
    Duration delay = getDelay(namespace);

    return new Backfill(jooq, batchSize, delay).execute();
  }

  private static int getBatchSize(Namespace namespace) {
    Integer batchSize = namespace.getInt(Args.BATCH_SIZE);
    return batchSize == null
        ? 1
        : batchSize;
  }

  private static Duration getDelay(Namespace namespace) {
    String delayToken = namespace.getString(Args.DELAY);
    return delayToken == null
        ? Duration.ZERO
        : Duration.parse(delayToken);
  }

  private static class Backfill {
    private final DSLContext jooq;
    private final int batchSize;
    private final Duration delay;

    public Backfill(
        DSLContext jooq,
        int batchSize,
        Duration delay) {
      this.jooq = jooq;
      this.batchSize = batchSize;
      this.delay = delay;
    }

    public int execute() {
      int nBatches = 0;

      Cursor<Record1<Long>> cursor = allSecretsWithoutOwners();
      try {
        while (cursor.hasNext()) {
          processBatch(cursor);
          nBatches++;
          System.out.print(".");
          sleepQuietly();
        }
      } finally {
        if (!cursor.isClosed()) {
          cursor.close();
        }
      }
      System.out.println();
      System.out.println(String.format("Ownership backfill complete. Batches: %s", nBatches));

      return nBatches;
    }

    private void processBatch(Cursor<Record1<Long>> cursor) {
      for (int i = 0; i < batchSize; i++) {
        if (cursor.hasNext()) {
          findAndUpdateOwner(cursor.fetchNext());
        } else {
          return;
        }
      }
    }

    private void sleepQuietly() {
      try {
        TimeUnit.MILLISECONDS.sleep(delay.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    private void findAndUpdateOwner(Record1<Long> record) {
      long secretId = record.value1();
      Optional<Long> maybeSecretOwner = getSecretOwner(secretId);
      if (maybeSecretOwner.isPresent()) {
        updateOwner(secretId, maybeSecretOwner.get());
      }
    }

    private Cursor<Record1<Long>> allSecretsWithoutOwners() {
      return jooq
          .select(SECRETS.ID)
          .from(SECRETS)
          .where(SECRETS.OWNER.isNull())
          .fetchSize(batchSize)
          .fetchLazy();
    }

    private void updateOwner(long secretId, long ownerId) {
      jooq.update(SECRETS)
          .set(SECRETS.OWNER, ownerId)
          .where(SECRETS.ID.eq(secretId))
          .and(SECRETS.OWNER.isNull())
          .execute();
    }

    private Optional<Long> getSecretOwner(long secretId) {
      Long ownerId = jooq
          .select(ACCESSGRANTS.GROUPID)
          .from(ACCESSGRANTS)
          .where(ACCESSGRANTS.SECRETID.eq(secretId))
          .orderBy(ACCESSGRANTS.CREATEDAT.asc())
          .limit(1)
          .fetchOne(ACCESSGRANTS.GROUPID);

      return Optional.ofNullable(ownerId);
    }
  }
}
