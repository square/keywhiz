package keywhiz.commands;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

    assertNoOwner(secretId);

    assertEquals(1, backfill());

    assertOwnerEquals(secretId, group1Id);
  }

  @Test
  public void doesNotOverwriteExistingOwner() {
    long group1Id = insertGroup();
    long secretId = insertSecretWithOwner(group1Id);
    long group2Id = insertGroup();

    grantAccess(secretId, group2Id);
    grantAccess(secretId, group1Id);

    assertOwnerEquals(secretId, group1Id);

    assertEquals(0, backfill());

    assertOwnerEquals(secretId, group1Id);
  }

  @Test
  public void doesNotModifySecretWithNoAccessGrants() {
    long secretId = insertSecret();

    assertNoOwner(secretId);

    assertEquals(1, backfill());

    assertNoOwner(secretId);
  }

  @Test
  public void backfillsMultipleBatches() {
    long secret1Id = insertSecret();
    long secret2Id = insertSecret();
    long secret3Id = insertSecret();

    long group1Id = insertGroup();
    long group2Id = insertGroup();
    long group3Id = insertGroup();

    grantAccess(secret1Id, group1Id);

    grantAccess(secret2Id, group2Id);
    grantAccess(secret2Id, group1Id);

    grantAccess(secret3Id, group3Id);
    grantAccess(secret3Id, group2Id);
    grantAccess(secret3Id, group1Id);

    assertNoOwner(secret1Id);
    assertNoOwner(secret2Id);
    assertNoOwner(secret3Id);

    assertEquals(3, backfill());

    assertOwnerEquals(secret1Id, group1Id);
    assertOwnerEquals(secret2Id, group2Id);
    assertOwnerEquals(secret3Id, group3Id);
  }

  private void assertNoOwner(long secretId) {
    Record1<Long> record = jooq.select(SECRETS.OWNER)
        .from(SECRETS)
        .where(SECRETS.ID.eq(secretId))
        .fetchOne();

    assertNotNull(record);
    assertNull(record.value1());
  }

  private void assertOwnerEquals(long secretId, long groupId) {
    Record1<Long> record = jooq.select(SECRETS.OWNER)
        .from(SECRETS)
        .where(SECRETS.ID.eq(secretId))
        .fetchOne();

    assertNotNull(record);
    assertEquals(Long.valueOf(groupId), record.value1());
  }

  private int backfill() {
    ServiceContext context = ServiceContext.create();
    Namespace namespace = new Namespace(Collections.emptyMap());
    return new BackfillOwnershipCommand().execute(context.getBootstrap(), namespace, context.getConfig());
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

    waitABit();
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

  private long insertSecretWithOwner(long ownerId) {
    long secretId = new Random().nextInt(Integer.MAX_VALUE);
    String secretName = UUID.randomUUID().toString();
    long now = Instant.now().toEpochMilli();

    jooq.insertInto(SECRETS)
        .set(SECRETS.ID, secretId)
        .set(SECRETS.NAME, secretName)
        .set(SECRETS.OWNER, ownerId)
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

  private static void waitABit() {
    try {
      TimeUnit.MILLISECONDS.sleep(2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
