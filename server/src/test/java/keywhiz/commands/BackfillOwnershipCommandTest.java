package keywhiz.commands;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.test.ServiceContext;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(KeywhizTestRunner.class)
public class BackfillOwnershipCommandTest {
  @Inject
  private DSLContext jooq;

  @Test
  public void backfillsOldestOwner() {
    long secretId = insertSecret();

    long group1Id = insertGroup();
    long group2Id = insertGroup();
    long group3Id = insertGroup();

    grantAccess(secretId, group1Id);
    grantAccess(secretId, group2Id);
    grantAccess(secretId, group3Id);

    backfill();

    assertOwnerEquals(secretId, group1Id);
  }

  private void assertOwnerEquals(long secretId, long groupId) {
    Record1<Long> record = jooq.select(SECRETS.OWNER)
        .from(SECRETS)
        .where(SECRETS.ID.eq(secretId))
        .fetchOne();

    assertNotNull(record);
    assertEquals(Long.valueOf(groupId), record.value1());
  }

  private void backfill() {
    ServiceContext context = ServiceContext.create();
    Namespace namespace = new Namespace(Collections.emptyMap());
    try {
      new BackfillOwnershipCommand().run(context.getBootstrap(), namespace, context.getConfig());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void grantAccess(long secretId, long groupId) {
    long grantId = new Random().nextInt(Integer.MAX_VALUE);
    long now = Instant.now().toEpochMilli();

    jooq.insertInto(ACCESSGRANTS)
        .set(ACCESSGRANTS.ID, grantId)
        .set(ACCESSGRANTS.SECRETID, secretId)
        .set(ACCESSGRANTS.GROUPID, groupId)
        .set(ACCESSGRANTS.CREATEDAT, now)
        .set(ACCESSGRANTS.UPDATEDAT, now)
        .execute();

    waitOneMillisecond();
  }

  private long insertSecret() {
    long secretId = new Random().nextInt(Integer.MAX_VALUE);
    String secretName = UUID.randomUUID().toString();
    long now = Instant.now().toEpochMilli();

    jooq.insertInto(SECRETS)
        .set(SECRETS.ID, secretId)
        .set(SECRETS.NAME, secretName)
        .set(SECRETS.CREATEDAT, now)
        .set(SECRETS.UPDATEDAT, now)
        .execute();

    return secretId;
  }

  private long insertGroup() {
    long groupId = new Random().nextInt(Integer.MAX_VALUE);
    String groupName = UUID.randomUUID().toString();
    long now = Instant.now().toEpochMilli();

    jooq.insertInto(GROUPS)
        .set(GROUPS.ID, groupId)
        .set(GROUPS.NAME, groupName)
        .set(GROUPS.CREATEDAT, now)
        .set(GROUPS.UPDATEDAT, now)
        .set(GROUPS.METADATA, "{}")
        .execute();

    return groupId;
  }

  private static void waitOneMillisecond() {
    try {
      TimeUnit.MILLISECONDS.sleep(1);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
