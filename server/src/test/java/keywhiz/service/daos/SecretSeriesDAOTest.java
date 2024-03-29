/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz.service.daos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.config.Readwrite;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.DeletedAccessgrants.DELETED_ACCESSGRANTS;
import static keywhiz.jooq.tables.DeletedSecrets.DELETED_SECRETS;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(KeywhizTestRunner.class)
public class SecretSeriesDAOTest {
  @Inject DSLContext jooqContext;
  @Inject @Readwrite SecretSeriesDAO secretSeriesDAO;
  @Inject @Readwrite SecretContentDAO secretContentDAO;
  @Inject @Readwrite GroupDAO groupDAO;
  @Inject AclDAO.AclDAOFactory aclDAOFactory;
  AclDAO aclDAO;

  @Before public void setUp() {
    aclDAO = aclDAOFactory.readwrite();
  }

  @Test
  public void listExpiringSecretNamesIncludesAllExpiringSecrets() {
    long expiration1 = randomExpiration();
    long expiration2 = randomExpiration();

    String secretSeriesName1 = createSecretSeriesAndContentWithExpiration(expiration1);
    String secretSeriesName2 = createSecretSeriesAndContentWithExpiration(expiration2);

    long notAfter = Math.max(expiration1, expiration2) + 1;

    List<String> expiringSecretNames = secretSeriesDAO.listExpiringSecretNames(Instant.ofEpochSecond(notAfter));
    assertThat(expiringSecretNames).containsExactlyInAnyOrder(secretSeriesName1, secretSeriesName2);
  }

  @Test
  public void listExpiringSecretNamesDoesNotIncludeSecretsExpiringOnNotAfter() {
    long expiration1 = randomExpiration();
    long expiration2 = expiration1 + 1;

    String secretSeriesName1 = createSecretSeriesAndContentWithExpiration(expiration1);
    String secretSeriesName2 = createSecretSeriesAndContentWithExpiration(expiration2);

    long notAfter = expiration2;

    List<String> expiringSecretNames = secretSeriesDAO.listExpiringSecretNames(Instant.ofEpochSecond(notAfter));
    assertThat(expiringSecretNames).containsExactlyInAnyOrder(secretSeriesName1);
  }

  @Test
  public void listExpiringSecretNamesDoesNotIncludeSecretsExpiringAfterNotAfter() {
    long expiration1 = randomExpiration();
    long expiration2 = expiration1 + 2;

    String secretSeriesName1 = createSecretSeriesAndContentWithExpiration(expiration1);
    String secretSeriesName2 = createSecretSeriesAndContentWithExpiration(expiration2);

    long notAfter = expiration1 + 1;

    List<String> expiringSecretNames = secretSeriesDAO.listExpiringSecretNames(Instant.ofEpochSecond(notAfter));
    assertThat(expiringSecretNames).containsExactlyInAnyOrder(secretSeriesName1);
  }

  private String createSecretSeriesAndContentWithExpiration(long expiration) {
    String secretSeriesName = randomName();
    long secretSeriesId = createSecretSeries(secretSeriesName);
    long secretContentId = createSecretContent(secretSeriesId);
    secretSeriesDAO.setExpiration(secretContentId, Instant.ofEpochSecond(expiration));
    return secretSeriesName;
  }

  @Test
  public void setCurrentVersionUpdatesSecretSeriesExpiry() {
    long secretSeriesId = createRandomSecretSeriesWithNoContent();

    long expiration1 = randomExpiration();
    long expiration2 = randomExpiration();

    long secretContentId1 = createSecretContent(secretSeriesId);
    secretSeriesDAO.setExpiration(secretContentId1, Instant.ofEpochSecond(expiration1));

    long secretContentId2 = createSecretContent(secretSeriesId);
    secretSeriesDAO.setExpiration(secretContentId2, Instant.ofEpochSecond(expiration2));

    {
      SecretsRecord secretSeriesRecord = secretSeriesDAO.getSecretSeriesRecordById(secretSeriesId);
      assertEquals(Long.valueOf(expiration2), secretSeriesRecord.getExpiry());
    }

    secretSeriesDAO.setCurrentVersion(secretSeriesId, secretContentId1, "updater", Instant.now().getEpochSecond());

    {
      SecretsRecord secretSeriesRecord = secretSeriesDAO.getSecretSeriesRecordById(secretSeriesId);
      assertEquals(Long.valueOf(expiration1), secretSeriesRecord.getExpiry());
    }
  }

  @Test
  public void setExpirationForNonCurrentVersionDoesNotUpdateSecretSeriesExpiry() {
    long secretSeriesId = createRandomSecretSeriesWithNoContent();

    long expiration1 = randomExpiration();
    long expiration2 = randomExpiration();

    long secretContentId1 = createSecretContent(secretSeriesId);
    secretSeriesDAO.setExpiration(secretContentId1, Instant.ofEpochSecond(expiration1));

    long secretContentId2 = createSecretContent(secretSeriesId);
    secretSeriesDAO.setExpiration(secretContentId2, Instant.ofEpochSecond(expiration2));

    {
      SecretsRecord secretSeriesRecord = secretSeriesDAO.getSecretSeriesRecordById(secretSeriesId);
      assertEquals(Long.valueOf(secretContentId2), secretSeriesRecord.getCurrent());
      assertEquals(Long.valueOf(expiration2), secretSeriesRecord.getExpiry());
    }

    long updatedExpiration = randomExpiration();
    secretSeriesDAO.setExpiration(secretContentId1, Instant.ofEpochSecond(updatedExpiration));

    {
      SecretsRecord secretSeriesRecord = secretSeriesDAO.getSecretSeriesRecordById(secretSeriesId);
      assertEquals(Long.valueOf(secretContentId2), secretSeriesRecord.getCurrent());
      assertEquals(Long.valueOf(expiration2), secretSeriesRecord.getExpiry());
    }
  }

  @Test
  public void setExpirationForCurrentVersionUpdatesSecretSeriesExpiry() {
    long secretSeriesId = createRandomSecretSeriesWithNoContent();
    long secretContentId = createSecretContent(secretSeriesId);

    {
      SecretsRecord secretSeriesRecord = secretSeriesDAO.getSecretSeriesRecordById(secretSeriesId);
      assertEquals(Long.valueOf(0), secretSeriesRecord.getExpiry());
      SecretContent secretContent = secretContentDAO.getSecretContentById(secretContentId).get();
      assertEquals(0L, secretContent.expiry());
    }

    long expiration = randomExpiration();
    secretSeriesDAO.setExpiration(secretContentId, Instant.ofEpochSecond(expiration));

    {
      SecretsRecord secretSeriesRecord = secretSeriesDAO.getSecretSeriesRecordById(secretSeriesId);
      assertEquals(Long.valueOf(expiration), secretSeriesRecord.getExpiry());
      SecretContent secretContent = secretContentDAO.getSecretContentById(secretContentId).get();
      assertEquals(expiration, secretContent.expiry());
    }
  }

  @Test
  public void secretSeriesExistsFindsSecretSeries() {
    String secretName = randomName();
    createSecretSeries(secretName);
    assertTrue(secretSeriesDAO.secretSeriesExists(secretName));
  }

  @Test
  public void secretSeriesExistsDoesNotFindMissingSecretSeries() {
    String secretName = randomName();
    assertFalse(secretSeriesDAO.secretSeriesExists(secretName));
  }

  @Test public void setsRowHmacByName() {
    String secretName = randomName();
    createSecretSeries(secretName);

    String newHmac = UUID.randomUUID().toString();
    secretSeriesDAO.setRowHmacByName(secretName, newHmac);

    String updatedHmac = jooqContext.fetchOne(SECRETS, SECRETS.NAME.eq(secretName)).getRowHmac();
    assertEquals(newHmac, updatedHmac);
  }

  @Test public void renameUpdatesRowHmac() {
    long id = createRandomSecretSeriesWithNoContent();
    String originalHmac = getRowHmac(id);

    String newName = randomName();
    secretSeriesDAO.renameSecretSeriesById(id, newName, "creator", ApiDate.now().toEpochSecond());
    String renamedHmac = getRowHmac(id);

    assertNotEquals(originalHmac, renamedHmac);
  }

  @Test public void createSecretSeriesUsesSuppliedId() {
    long wellKnownId = 123;

    long id = secretSeriesDAO.createSecretSeries(
        wellKnownId,
        "abc",
        null,
        null,
        null,
        null,
        null,
        ApiDate.now().toEpochSecond());

    assertEquals(wellKnownId, id);
  }

  @Test public void computesWellKnownRowHmac() {
    long wellKnownId = 123;

    secretSeriesDAO.createSecretSeries(
        wellKnownId,
        "abc",
        null,
        null,
        null,
        null,
        null,
        ApiDate.now().toEpochSecond());

    assertEquals(
        "649F23AF8C88F32AAEDD848EB5F42A8770BBE59B6DBE20E66DDA2AB915E28876",
        getRowHmac(wellKnownId));
  }

  @Test public void ownerRoundTrip() {
    String ownerName = "foo";
    long ownerId = groupDAO.createGroup(
        ownerName,
        null,
        null,
        ImmutableMap.of());

    long secretId = secretSeriesDAO.createSecretSeries(
        "name",
        ownerId,
        null,
        null,
        null,
        null,
        ApiDate.now().toEpochSecond());

    createSecretContent(secretId);

    SecretSeries series = secretSeriesDAO.getSecretSeriesById(secretId).get();
    assertEquals(ownerName, series.owner());
  }

  @Test
  public void failsToLoadSecretWithNonExistentOwner() {
    long ownerId = new Random().nextInt(Integer.MAX_VALUE);
    assertFalse(groupDAO.getGroupById(ownerId).isPresent());

    long secretId = secretSeriesDAO.createSecretSeries(
        "name",
        ownerId,
        null,
        null,
        null,
        null,
        ApiDate.now().toEpochSecond());

    createSecretContent(secretId);

    assertThrows(IllegalStateException.class, () -> secretSeriesDAO.getSecretSeriesById(secretId));
  }

  @Test public void createAndLookupSecretSeries() {
    int before = tableSize();
    long now = OffsetDateTime.now().toEpochSecond();
    ApiDate nowDate = new ApiDate(now);

    long id = secretSeriesDAO.createSecretSeries("newSecretSeries", null, "creator", "desc", null,
        ImmutableMap.of("foo", "bar"), now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);

    SecretSeries expected =
        SecretSeries.of(id, "newSecretSeries", null, "desc", nowDate, "creator", nowDate,
            "creator", null, ImmutableMap.of("foo", "bar"), contentId);

    assertThat(tableSize()).isEqualTo(before + 1);

    SecretSeries actual = secretSeriesDAO.getSecretSeriesById(id)
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualTo(expected);

    actual = secretSeriesDAO.getSecretSeriesByName("newSecretSeries")
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
        "name", "description", "type", "generationOptions", "currentVersion");
  }

  @Test public void setCurrentVersion() {
    long now = OffsetDateTime.now().toEpochSecond();

    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesByName", null, "creator",
        "", null, null, now);
    Optional<SecretSeries> secretSeriesById = secretSeriesDAO.getSecretSeriesById(id);
    assertThat(secretSeriesById).isEmpty();

    long contentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "updater", now + 3600);

    secretSeriesById = secretSeriesDAO.getSecretSeriesById(id);
    assertThat(secretSeriesById.get().currentVersion().get()).isEqualTo(contentId);
    assertThat(secretSeriesById.get().updatedBy()).isEqualTo("updater");
    assertThat(secretSeriesById.get().updatedAt().toEpochSecond()).isEqualTo(now + 3600);
  }

  @Test(expected = IllegalStateException.class)
  public void setCurrentVersion_failsWithIncorrectSecretContent() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("someSecret", null, "creator", "",
        null, null, now);
    long other = secretSeriesDAO.createSecretSeries("someOtherSecret", null, "creator", "",
        null, null, now);
    long contentId = secretContentDAO.createSecretContent(other, "blah",
        "checksum", "creator", null, 0, now);

    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
  }

  @Test public void deleteSecretSeriesByName() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesByName", null, "creator",
        "", null, null, now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName")
        .get()
        .currentVersion())
        .isPresent();

    secretSeriesDAO.softDeleteSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName");
    assertThat(
        secretSeriesDAO.getSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName")).isEmpty();
    assertThat(secretSeriesDAO.getSecretSeriesById(id)).isEmpty();
  }

  @Test public void deleteSecretSeriesByNameAndRecreate() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeDeletedAndReplaced", null, "creator",
        "", null, null, now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeletedAndReplaced")
        .get()
        .currentVersion())
        .isPresent();

    secretSeriesDAO.softDeleteSecretSeriesByName("toBeDeletedAndReplaced");
    assertThat(
        secretSeriesDAO.getSecretSeriesByName("toBeDeletedAndReplaced")).isEmpty();
    assertThat(secretSeriesDAO.getSecretSeriesById(id)).isEmpty();

    id = secretSeriesDAO.createSecretSeries("toBeDeletedAndReplaced", null, "creator",
        "", null, null, now);
    contentId = secretContentDAO.createSecretContent(id, "blah2",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeletedAndReplaced")
        .get()
        .currentVersion())
        .isPresent();
  }

  @Test public void deleteSecretSeriesById() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesById",
        null, "creator", "", null, null, now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).get().currentVersion()).isPresent();

    secretSeriesDAO.softDeleteSecretSeriesById(id);
    assertThat(secretSeriesDAO.getSecretSeriesById(id)).isEmpty();
  }

  @Test public void renameSecretSeriesById() {
    long now = OffsetDateTime.now().toEpochSecond();
    String oldName = "toBeRenamed_renameSecretSeriesById";
    long id = secretSeriesDAO.createSecretSeries(oldName,
        null, "creator", "", null, null, now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName(oldName).get().currentVersion()).isPresent();

    String newName = "newName";
    secretSeriesDAO.renameSecretSeriesById(id, newName, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName(newName).get().currentVersion()).isPresent();
  }

  @Test public void updateSecretSeriesContentById() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeUpdated_updateSecretSeriesContentById",
        null, "creator", "", null, null, now);
    long oldContentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, oldContentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).get().currentVersion().get())
        .isEqualTo(oldContentId);

    long newContentId = secretContentDAO.createSecretContent(id, "newblah",
        "checksum", "creator", null, 0, now);

    secretSeriesDAO.setCurrentVersion(id, newContentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).get().currentVersion().get())
        .isEqualTo(newContentId);
  }

  @Test public void getSecretSeriesByDeletedName() {
    String secretName = "toBeFound_getSecretSeriesByDeletedName";
    long secretSeriesID = createSoftDeletedSecretSeries(secretName);

    List<SecretSeries> deletedSecretSeries =
        secretSeriesDAO.getSecretSeriesByDeletedName(secretName);
    assertThat(deletedSecretSeries.size()).isEqualTo(1);
    assertThat(deletedSecretSeries.get(0).name()).isEqualTo(secretName);
    assertThat(deletedSecretSeries.get(0).id()).isEqualTo(secretSeriesID);
  }

  @Test public void getDeletedSecretSeriesById() {
    String secretName = "toBeFound_getDeletedSecretSeriesById";
    long secretSeriesID = createSoftDeletedSecretSeries(secretName);

    assertThat(secretSeriesDAO.getSecretSeriesById(secretSeriesID)).isEmpty();

    Optional<SecretSeries> deletedSecretSeries =
        secretSeriesDAO.getDeletedSecretSeriesById(secretSeriesID);
    assertThat(deletedSecretSeries).isPresent();
    assertThat(deletedSecretSeries.get().name()).isEqualTo(secretName);
    assertThat(deletedSecretSeries.get().id()).isEqualTo(secretSeriesID);
  }

  @Test public void getDeletedSecretSeriesByName() {
    String secretName = "getDeletedSecretSeriesByName";
    long bothTablesID = createSoftDeletedSecretSeries(secretName);
    long secretsTableOnlyID = createLegacySoftDeletedSecretSeries(secretName);
    long notDeletedID = createSecretSeries(secretName);

    List<SecretSeries> deletedSecrets =
        secretSeriesDAO.getSecretSeriesByDeletedName(secretName);

    List<Long> deletedIDs = deletedSecrets.stream().map(s -> s.id()).collect(Collectors.toList());
    assertThat(deletedIDs).containsExactlyInAnyOrder(bothTablesID, secretsTableOnlyID);
    assertThat(deletedIDs).doesNotContain(notDeletedID);

    // This checks that the SecretSeries returned for bothTablesID is actually the row from
    // `deleted_secrets`, since the `current` field on the row in `secrets` will have been set to
    // NULL
    SecretSeries foundSecretSeriesFromBothTables =
        deletedSecrets.stream().filter(s -> s.id() == bothTablesID).findFirst().get();
    assertThat(foundSecretSeriesFromBothTables.currentVersion().get()).isNotNull();

    // But the one that doesn't have a row in `deleted_secrets` will have a NULL currentVersion.
    SecretSeries foundSecretSeriesFromSecretsTableOnly =
        deletedSecrets.stream().filter(s -> s.id() == secretsTableOnlyID).findFirst().get();
    assertThat(foundSecretSeriesFromSecretsTableOnly.currentVersion().isPresent()).isFalse();
  }

  @Test public void deleteSecretAccessGrants() {
    long secretSeriesID = createSecretSeries("deleteSecretAccessGrants");
    long groupID = groupDAO.createGroup("group1", "creator", "", ImmutableMap.of());
    aclDAO.allowAccess(jooqContext.configuration(), secretSeriesID, groupID);

    assertThat(jooqContext.select(ACCESSGRANTS.ID)
        .from(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(secretSeriesID))).isNotEmpty();
    assertThat(jooqContext.select(DELETED_ACCESSGRANTS.ID)
        .from(DELETED_ACCESSGRANTS)
        .where(DELETED_ACCESSGRANTS.SECRETID.eq(secretSeriesID))).isEmpty();

    secretSeriesDAO.softDeleteSecretSeriesById(secretSeriesID);

    assertThat(jooqContext.select(ACCESSGRANTS.ID)
        .from(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(secretSeriesID))).isEmpty();
    assertThat(jooqContext.select(DELETED_ACCESSGRANTS.ID)
        .from(DELETED_ACCESSGRANTS)
        .where(DELETED_ACCESSGRANTS.SECRETID.eq(secretSeriesID))).isNotEmpty();
  }

  @Test public void deletedAccessGrantDoesNotRequireSameIDAsAccessGrant() {
    long secretSeriesID = createSecretSeries("deleteSecretAccessGrants");
    long groupID = groupDAO.createGroup("group1", "creator", "", ImmutableMap.of());
    aclDAO.allowAccess(jooqContext.configuration(), secretSeriesID, groupID);

    long idFromAccessGrantsTable = jooqContext.select(ACCESSGRANTS.ID)
        .from(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(secretSeriesID))
        .fetch()
        .get(0)
        .value1();

    // Ensure this ID is already used in DELETED_ACCESSGRANTS
    jooqContext.insertInto(DELETED_ACCESSGRANTS)
        .set(ACCESSGRANTS.ID, idFromAccessGrantsTable)
        .set(ACCESSGRANTS.GROUPID, 123L)
        .set(ACCESSGRANTS.SECRETID, 456L)
        .set(ACCESSGRANTS.ROW_HMAC, "HMAC")
        .set(ACCESSGRANTS.UPDATEDAT, 7L)
        .set(ACCESSGRANTS.CREATEDAT, 6L)
        .execute();

    // This should not throw, since a new ID that is *not* already in use in DELETED_ACCESSGRANTS
    // should be picked for the new row
    secretSeriesDAO.softDeleteSecretSeriesById(secretSeriesID);

    List<Record1<Long>> idsInDeletedAccessGrants = jooqContext.select(DELETED_ACCESSGRANTS.ID)
        .from(DELETED_ACCESSGRANTS)
        .where(DELETED_ACCESSGRANTS.SECRETID.eq(secretSeriesID))
        .fetch();

    assertThat(idsInDeletedAccessGrants.size()).isEqualTo(1);
    assertThat(idsInDeletedAccessGrants.get(0).value1()).isNotEqualTo(idFromAccessGrantsTable);
  }

  @Test public void undeleteSecret() {
    long secretSeriesID = createSecretSeries("undeleteSecret");
    long groupID = groupDAO.createGroup("group1", "creator", "", ImmutableMap.of());
    aclDAO.allowAccess(jooqContext.configuration(), secretSeriesID, groupID);

    secretSeriesDAO.softDeleteSecretSeriesById(secretSeriesID);
    secretSeriesDAO.undeleteSoftDeletedSecretSeriesById(secretSeriesID);

    Optional<SecretSeries> loadedSecretSeries = secretSeriesDAO.getSecretSeriesById(secretSeriesID);
    assertThat(loadedSecretSeries).isPresent();
    assertThat(loadedSecretSeries.get().currentVersion()).isNotNull();
    assertThat(loadedSecretSeries.get().name()).isEqualTo("undeleteSecret");

    Optional<SecretSeries> deletedSecretSeries =
        secretSeriesDAO.getDeletedSecretSeriesById(secretSeriesID);
    assertThat(deletedSecretSeries).isEmpty();

    assertThat(jooqContext.select(ACCESSGRANTS.ID)
        .from(ACCESSGRANTS)
        .where(ACCESSGRANTS.SECRETID.eq(secretSeriesID))).isNotEmpty();
    assertThat(jooqContext.select(DELETED_ACCESSGRANTS.ID)
        .from(DELETED_ACCESSGRANTS)
        .where(DELETED_ACCESSGRANTS.SECRETID.eq(secretSeriesID))).isEmpty();
  }

  @Test public void undeleteFailsOnLegacySecret() {
    long secretSeriesID = createLegacySoftDeletedSecretSeries("undeleteSecret");

    assertThrows(DataAccessException.class, () -> {
      secretSeriesDAO.undeleteSoftDeletedSecretSeriesById(secretSeriesID);
    });
  }

  private long createLegacySoftDeletedSecretSeries(String name) {
    long secretSeriesID = createSoftDeletedSecretSeries(name);
    jooqContext.delete(DELETED_SECRETS).where(DELETED_SECRETS.ID.eq(secretSeriesID)).execute();
    return secretSeriesID;
  }

  private long createSoftDeletedSecretSeries(String name) {
    long secretSeriesID = createSecretSeries(name);
    secretSeriesDAO.softDeleteSecretSeriesById(secretSeriesID);
    return secretSeriesID;
  }

  private long createSecretSeries(String name) {
    long now = OffsetDateTime.now().toEpochSecond();
    long secretSeriesID = secretSeriesDAO.createSecretSeries(name, null, "creator", "description", null, null, now);
    long contentID = secretContentDAO.createSecretContent(secretSeriesID, "encrypted_content", "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(secretSeriesID, contentID, "creator", now);
    return secretSeriesID;
  }

  @Test public void countDeletedSecretSeries() {
    assertThat(secretSeriesDAO.countDeletedSecretSeries()).isEqualTo(0);

    createLegacySoftDeletedSecretSeries("countDeletedSecretSeries");
    assertThat(secretSeriesDAO.countDeletedSecretSeries()).isEqualTo(1);

    createSoftDeletedSecretSeries("countDeletedSecretSeries");
    assertThat(secretSeriesDAO.countDeletedSecretSeries()).isEqualTo(2);
  }

  @Test public void getNonExistentSecretSeries() {
    assertThat(secretSeriesDAO.getSecretSeriesByName("non-existent")).isEmpty();
    assertThat(secretSeriesDAO.getSecretSeriesById(-2328)).isEmpty();
  }

  @Test
  public void getSecretSeries() {
    // Create multiple secret series
    long now = OffsetDateTime.now().toEpochSecond();
    long firstExpiry = now + 10000;
    long secondExpiry = now + 20000;
    long thirdExpiry = now + 30000;
    long fourthExpiry = now + 40000;
    long firstId = secretSeriesDAO.createSecretSeries("expiringFirst",
        null, "creator", "", null, null, now);
    long firstContentId = secretContentDAO.createSecretContent(firstId,
        "blah", "checksum", "creator", null, firstExpiry, now);
    secretSeriesDAO.setCurrentVersion(firstId, firstContentId, "creator", now);

    long secondId = secretSeriesDAO.createSecretSeries("expiringSecond",
        null, "creator", "", null, null, now);
    long secondContentId = secretContentDAO.createSecretContent(secondId, "blah",
        "checksum", "creator", null, secondExpiry, now);
    secretSeriesDAO.setCurrentVersion(secondId, secondContentId, "creator", now);

    // Make sure the rows aren't ordered by expiry
    long fourthId = secretSeriesDAO.createSecretSeries("expiringFourth",
        null, "creator", "", null, null, now);
    long fourthContentId = secretContentDAO.createSecretContent(fourthId, "blah",
        "checksum", "creator", null, fourthExpiry, now);
    secretSeriesDAO.setCurrentVersion(fourthId, fourthContentId, "creator", now);

    long thirdId = secretSeriesDAO.createSecretSeries("expiringThird",
        null, "creator", "", null, null, now);
    long thirdContentId = secretContentDAO.createSecretContent(thirdId, "blah",
        "checksum", "creator", null, thirdExpiry, now);
    secretSeriesDAO.setCurrentVersion(thirdId, thirdContentId, "creator", now);

    long fifthId = secretSeriesDAO.createSecretSeries("laterInAlphabetExpiringFourth",
        null, "creator", "", null, null, now);
    long fifthContentId = secretContentDAO.createSecretContent(fifthId, "blah",
        "checksum", "creator", null, fourthExpiry, now);
    secretSeriesDAO.setCurrentVersion(fifthId, fifthContentId, "creator", now);

    // Retrieving secrets with no parameters should retrieve all created secrets (although given
    // the shared database, it's likely to also retrieve others)
    ImmutableList<SecretSeries>
        retrievedSeries = secretSeriesDAO.getSecretSeries(null, null, null, null, null);
    assertListContainsSecretsWithIds(retrievedSeries, ImmutableList.of(firstId, secondId, thirdId, fourthId, fifthId));

    // Restrict expireMaxTime to exclude the last secrets
    retrievedSeries = secretSeriesDAO.getSecretSeries(fourthExpiry - 100, null, null,null, null);
    assertListContainsSecretsWithIds(retrievedSeries, ImmutableList.of(firstId, secondId, thirdId));
    assertListDoesNotContainSecretsWithIds(retrievedSeries, ImmutableList.of(fourthId, fifthId));

    // Restrict expireMinTime to exclude the first secret
    retrievedSeries = secretSeriesDAO.getSecretSeries(fourthExpiry - 100, null, firstExpiry + 10, null,null);
    assertListContainsSecretsWithIds(retrievedSeries, ImmutableList.of(secondId, thirdId));
    assertListDoesNotContainSecretsWithIds(retrievedSeries, ImmutableList.of(firstId, fourthId, fifthId));

    // Adjust the limit to exclude the third secret
    retrievedSeries = secretSeriesDAO.getSecretSeries(fourthExpiry - 100, null, firstExpiry + 10, null,1);
    assertListContainsSecretsWithIds(retrievedSeries, ImmutableList.of(secondId));
    assertListDoesNotContainSecretsWithIds(retrievedSeries, ImmutableList.of(firstId, thirdId, fourthId, fifthId));

    // Restrict the minName to exclude the fourth secret
    retrievedSeries = secretSeriesDAO.getSecretSeries(null, null, fourthExpiry, "laterInAlphabetExpiringFourth", null);
    assertListContainsSecretsWithIds(retrievedSeries, ImmutableList.of(fifthId));
    assertListDoesNotContainSecretsWithIds(retrievedSeries, ImmutableList.of(firstId, secondId, thirdId, fourthId));
  }

  private void assertListContainsSecretsWithIds(List<SecretSeries> secrets, List<Long> ids) {
    Set<Long> foundIds = new HashSet<>();
    for (SecretSeries secret : secrets) {
      if (ids.contains(secret.id())) {
        foundIds.add(secret.id());
      }
    }
    assertThat(foundIds).as("List should contain secrets with IDs %s; found IDs %s in secret list %s", ids, foundIds, secrets)
        .containsExactlyInAnyOrderElementsOf(ids);
  }

  private void assertListDoesNotContainSecretsWithIds(List<SecretSeries> secrets, List<Long> ids) {
    Set<Long> foundIds = new HashSet<>();
    for (SecretSeries secret : secrets) {
      if (ids.contains(secret.id())) {
        foundIds.add(secret.id());
      }
    }
    assertThat(foundIds).as("List should NOT contain secrets with IDs %s; found IDs %s in secret list %s", ids, foundIds, secrets)
        .isEmpty();
  }

  private int tableSize() {
    return jooqContext.fetchCount(SECRETS);
  }

  @Test public void getMultipleSecretSeriesByNameReturnsOne() {
    int before = tableSize();
    long now = OffsetDateTime.now().toEpochSecond();
    ApiDate nowDate = new ApiDate(now);

    long id = secretSeriesDAO.createSecretSeries("newSecretSeries", null, "creator", "desc", null,
            ImmutableMap.of("foo", "bar"), now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
            "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);

    List<SecretSeries> expected =
            List.of(SecretSeries.of(id, "newSecretSeries", null, "desc", nowDate, "creator", nowDate,
                    "creator", null, ImmutableMap.of("foo", "bar"), contentId));

    assertThat(tableSize()).isEqualTo(before + 1);


    List<SecretSeries> actual = secretSeriesDAO.getMultipleSecretSeriesByName(List.of("newSecretSeries"));
    assertThat(actual).isEqualTo(expected);
  }

  @Test public void getMultipleSecretSeriesByNameDuplicatesReturnsOne() {
    int before = tableSize();
    long now = OffsetDateTime.now().toEpochSecond();
    ApiDate nowDate = new ApiDate(now);

    long id = secretSeriesDAO.createSecretSeries("newSecretSeries", null, "creator", "desc", null,
            ImmutableMap.of("foo", "bar"), now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
            "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);

    List<SecretSeries> expected =
            List.of(SecretSeries.of(id, "newSecretSeries", null, "desc", nowDate, "creator", nowDate,
                    "creator", null, ImmutableMap.of("foo", "bar"), contentId));

    assertThat(tableSize()).isEqualTo(before + 1);


    // Requesting same secret multiple times - should yield one result
    List<SecretSeries> actual = secretSeriesDAO.getMultipleSecretSeriesByName(List.of("newSecretSeries", "newSecretSeries", "newSecretSeries"));
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void getNonExistentMultipleSecretSeriesByName() {
    assertThat(secretSeriesDAO.getMultipleSecretSeriesByName(List.of("non-existent"))).isEmpty();
  }

  @Test
  public void getMultipleSecretSeriesByName() {
    int before = tableSize();
    long now = OffsetDateTime.now().toEpochSecond();
    ApiDate nowDate = new ApiDate(now);

    long id = secretSeriesDAO.createSecretSeries("newSecretSeries", null, "creator", "desc", null,
            ImmutableMap.of("foo", "bar"), now);
    long contentId = secretContentDAO.createSecretContent(id, "blah",
            "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);

    long id2 = secretSeriesDAO.createSecretSeries("newSecretSeries2", null, "creator", "desc", null,
            ImmutableMap.of("f00", "b4r"), now);
    long contentId2 = secretContentDAO.createSecretContent(id2, "blah",
            "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id2, contentId2, "creator", now);

    assertThat(tableSize()).isEqualTo(before + 2);

    SecretSeries expected1 = SecretSeries.of(id, "newSecretSeries", null, "desc", nowDate, "creator", nowDate,
            "creator", null, ImmutableMap.of("foo", "bar"), contentId);
    SecretSeries expected2 = SecretSeries.of(id2, "newSecretSeries2", null, "desc", nowDate, "creator", nowDate,
            "creator", null, ImmutableMap.of("f00", "b4r"), contentId2);

    List<SecretSeries> actual = secretSeriesDAO.getMultipleSecretSeriesByName(List.of("newSecretSeries", "newSecretSeries2"));

    assertThat(actual).contains(expected1);
    assertThat(actual).contains(expected2);
  }

  private String getRowHmac(long secretSeriesId) {
    SecretsRecord r = jooqContext.fetchOne(SECRETS, SECRETS.ID.eq(secretSeriesId));
    String rowHmac = r.getValue(SECRETS.ROW_HMAC);
    return rowHmac;
  }

  private long createSecretSeriesWithNoContent(String name) {
    long id = secretSeriesDAO.createSecretSeries(
        name,
        null,
        null,
        null,
        null,
        null,
        ApiDate.now().toEpochSecond());
    return id;
  }

  private long createRandomSecretSeriesWithNoContent() {
    return createSecretSeriesWithNoContent(randomName());
  }

  private long createSecretContent(long secretId) {
    long now = OffsetDateTime.now().toEpochSecond();

    long contentId = secretContentDAO.createSecretContent(
        secretId,
        "blah",
        "checksum",
        "creator",
        Collections.emptyMap(),
        0,
        now);

    secretSeriesDAO.setCurrentVersion(secretId, contentId, "creator", now);

    return contentId;
  }

  private static String randomName() {
    return UUID.randomUUID().toString();
  }

  private static long randomExpiration() {
    long expiration = 0;
    Random random = new Random();

    for (;;) {
      expiration = random.nextLong();

      if (expiration <= 0) {
        continue;
      }

      try {
        Instant.ofEpochSecond(expiration);
      } catch (DateTimeException e) {
        continue;
      }

      break;
    }

    return expiration;
  }
}

