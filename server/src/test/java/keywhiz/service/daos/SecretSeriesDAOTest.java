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
import java.util.Random;
import java.util.UUID;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import keywhiz.service.config.Readwrite;
import org.jooq.DSLContext;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

@RunWith(KeywhizTestRunner.class)
public class SecretSeriesDAOTest {
  @Inject DSLContext jooqContext;
  @Inject @Readwrite SecretSeriesDAO secretSeriesDAO;
  @Inject @Readwrite SecretContentDAO secretContentDAO;
  @Inject @Readwrite GroupDAO groupDAO;

  @Test public void setsRowHmacByName() {
    String secretName = randomName();
    createSecretSeries(secretName);

    String newHmac = UUID.randomUUID().toString();
    secretSeriesDAO.setRowHmacByName(secretName, newHmac);

    String updatedHmac = jooqContext.fetchOne(SECRETS, SECRETS.NAME.eq(secretName)).getRowHmac();
    assertEquals(newHmac, updatedHmac);
  }

  @Test public void renameUpdatesRowHmac() {
    long id = createRandomSecretSeries();
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

    secretSeriesDAO.deleteSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName");
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

    secretSeriesDAO.deleteSecretSeriesByName("toBeDeletedAndReplaced");
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

    secretSeriesDAO.deleteSecretSeriesById(id);
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
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeFound_getSecretSeriesByDeletedName",
        null, "creator", "", null, null, now);
    long oldContentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, oldContentId, "creator", now);
    secretSeriesDAO.deleteSecretSeriesById(id);

    List<SecretSeries> deletedSecretSeries =
        secretSeriesDAO.getSecretSeriesByDeletedName("toBeFound_getSecretSeriesByDeletedName");
    assertThat(deletedSecretSeries.size()).isEqualTo(1);
    assertThat(deletedSecretSeries.get(0).name()).contains("toBeFound_getSecretSeriesByDeletedName");
    assertThat(deletedSecretSeries.get(0).name()).isNotEqualTo("toBeFound_getSecretSeriesByDeletedName");
    assertThat(deletedSecretSeries.get(0).id()).isEqualTo(id);
  }

  @Test public void getDeletedSecretSeriesById() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeFound_getSecretSeriesByDeletedId",
        null, "creator", "", null, null, now);
    long oldContentId = secretContentDAO.createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, oldContentId, "creator", now);
    secretSeriesDAO.deleteSecretSeriesById(id);
    assertThat(secretSeriesDAO.getSecretSeriesById(id)).isEmpty();

    Optional<SecretSeries> deletedSecretSeries = secretSeriesDAO.getDeletedSecretSeriesById(id);
    assertThat(deletedSecretSeries).isPresent();
    assertThat(deletedSecretSeries.get().name()).contains("toBeFound_getSecretSeriesByDeletedId");
    assertThat(deletedSecretSeries.get().name()).isNotEqualTo("toBeFound_getSecretSeriesByDeletedId");
    assertThat(deletedSecretSeries.get().id()).isEqualTo(id);
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

  private long createSecretSeries(String name) {
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

  private long createRandomSecretSeries() {
    return createSecretSeries(randomName());
  }

  private long createSecretContent(long secretId) {
    long now = OffsetDateTime.now().toEpochSecond();

    long contentId = secretContentDAO.createSecretContent(
        secretId,
        "blah",
        "checksum",
        "creator",
        null,
        0,
        now);

    secretSeriesDAO.setCurrentVersion(secretId, contentId, "creator", now);

    return contentId;
  }

  private static String randomName() {
    return UUID.randomUUID().toString();
  }
}

