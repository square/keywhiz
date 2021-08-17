package keywhiz.service.daos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.SanitizedSecretWithGroupsListAndCursor;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretRetrievalCursor;
import keywhiz.service.config.Readwrite;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(KeywhizTestRunner.class)
public class SecretControllerTest {
  private long now = OffsetDateTime.now().toEpochSecond();
  private long firstExpiry = now + 10000;
  private long secondExpiry = now + 20000;
  private long thirdExpiry = now + 30000;
  private long fourthExpiry = now + 40000;

  private long firstId;
  private long secondId;
  private long thirdId;
  private long fourthId;
  private long fifthId;

  @Inject DSLContext jooqContext;
  @Inject @Readwrite SecretSeriesDAO secretSeriesDAO;
  @Inject @Readwrite SecretContentDAO secretContentDAO;
  @Inject @Readwrite GroupDAO groupDAO;
  @Inject SecretController secretController;

  @Before public void setUp() {
    // create secrets
    firstId = createSecret(CreateSecretRequestV2.builder()
        .name("expiringFirst")
        .expiry(firstExpiry)
        .content("blah")
        .build());

    secondId = createSecret(CreateSecretRequestV2.builder()
        .name("expiringSecond")
        .expiry(secondExpiry)
        .content("blah")
        .build());

    fourthId = createSecret(CreateSecretRequestV2.builder()
        .name("expiringFourth")
        .expiry(fourthExpiry)
        .content("blah")
        .build());

    thirdId = createSecret(CreateSecretRequestV2.builder()
        .name("expiringThird")
        .expiry(thirdExpiry)
        .content("blah")
        .build());

    fifthId = createSecret(CreateSecretRequestV2.builder()
        .name("laterInAlphabetExpiringFourth")
        .expiry(fourthExpiry)
        .content("blah")
        .build());
  }

  @Test
  public void createsSecretWithOwner() {
    String ownerName = UUID.randomUUID().toString();
    groupDAO.createGroup(ownerName, "creator", "description", ImmutableMap.of());

    String secretName = UUID.randomUUID().toString();
    Secret created = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName)
        .create();

    assertEquals(ownerName, created.getOwner());
  }

  @Test
  public void createsOrUpdatesNewSecretWithOwner() {
    String ownerName = UUID.randomUUID().toString();

    groupDAO.createGroup(ownerName, "creator", "description", ImmutableMap.of());

    String secretName = UUID.randomUUID().toString();

    Secret created = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName)
        .createOrUpdate();

    assertEquals(ownerName, created.getOwner());
  }

  @Test
  public void createsOrUpdatesExistingSecretWithOwner() {
    String ownerName = UUID.randomUUID().toString();

    groupDAO.createGroup(ownerName, "creator", "description", ImmutableMap.of());

    String secretName = UUID.randomUUID().toString();

    Secret created = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName)
        .create();

    Secret updated = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName)
        .createOrUpdate();

    assertEquals(ownerName, updated.getOwner());
  }

  // Updating ownership will come in a future phase.
  // For now, documenting current behavior.
  @Test
  public void updateDoesNotOverwriteOwner() {
    String ownerName1 = UUID.randomUUID().toString();
    String ownerName2 = UUID.randomUUID().toString();

    groupDAO.createGroup(ownerName1, "creator", "description", ImmutableMap.of());
    groupDAO.createGroup(ownerName2, "creator", "description", ImmutableMap.of());

    String secretName = UUID.randomUUID().toString();

    Secret created = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName1)
        .create();

    Secret updated = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName2)
        .createOrUpdate();

    assertEquals(ownerName1, updated.getOwner());
  }

  @Test
  public void loadsSecretWithOwnerById() {
    String ownerName = UUID.randomUUID().toString();

    groupDAO.createGroup(ownerName, "creator", "description", ImmutableMap.of());

    String secretName = UUID.randomUUID().toString();

    Secret created = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName)
        .create();

    Secret persisted = secretController.getSecretById(created.getId()).get();
    assertEquals(ownerName, persisted.getOwner());
  }

  @Test
  public void loadsSecretWithOwnerByName() {
    String ownerName = UUID.randomUUID().toString();

    groupDAO.createGroup(ownerName, "creator", "description", ImmutableMap.of());

    String secretName = UUID.randomUUID().toString();

    Secret created = secretController
        .builder(secretName, "secret", "creator", now)
        .withOwnerName(ownerName)
        .create();

    Secret persisted = secretController.getSecretByName(secretName).get();
    assertEquals(ownerName, persisted.getOwner());
  }

  @Test
  public void getSanitizedSecretsWithGroupsAndCursor_allSecrets() {
    // Retrieving secrets with no parameters should retrieve all created secrets (although given
    // the shared database, it's likely to also retrieve others)
    List<SanitizedSecretWithGroups> retrievedSecrets = getAllSecretsWithCursor(null, null);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId, fourthId, fifthId));

    retrievedSecrets = getAllSecretsWithCursor(null, 4);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId, fourthId, fifthId));

    retrievedSecrets = getAllSecretsWithCursor(null, 3);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId, fourthId, fifthId));

    retrievedSecrets = getAllSecretsWithCursor(null, 1);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId, fourthId, fifthId));
  }

  @Test
  public void getSanitizedSecretsWithGroupsAndCursor_maxExpiryRestricted() {
    // Retrieving secrets with no parameters should retrieve all created secrets (although given
    // the shared database, it's likely to also retrieve others)
    List<SanitizedSecretWithGroups> retrievedSecrets =
        getAllSecretsWithCursor(fourthExpiry - 100, null);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId));
    assertListDoesNotContainSecretsWithIds(retrievedSecrets, ImmutableList.of(fourthId, fifthId));

    retrievedSecrets = getAllSecretsWithCursor(fourthExpiry - 100, 4);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId));
    assertListDoesNotContainSecretsWithIds(retrievedSecrets, ImmutableList.of(fourthId, fifthId));

    retrievedSecrets = getAllSecretsWithCursor(fourthExpiry - 100, 1);
    assertListContainsSecretsWithIds(retrievedSecrets,
        ImmutableList.of(firstId, secondId, thirdId));
    assertListDoesNotContainSecretsWithIds(retrievedSecrets, ImmutableList.of(fourthId, fifthId));
  }

  /**
   * Get all secrets matching the given criteria, using the cursor.  (This verifies that even if
   * the cursor's implementation changes slightly, the underlying behavior remains the same).
   *
   * @param expireMaxTime the maximum expiration time to return
   * @param limit the maximum number of records to return per batch
   * @return a list of secrets matching the criteria above
   */
  private List<SanitizedSecretWithGroups> getAllSecretsWithCursor(Long expireMaxTime, Integer limit) {
    List<SanitizedSecretWithGroups> allRetrievedSecrets = new ArrayList<>();
    SecretRetrievalCursor cursor = null;
    do {
      SanitizedSecretWithGroupsListAndCursor retrievedSecretsAndCursor =
          secretController.getSanitizedSecretsWithGroupsAndCursor(null, expireMaxTime, limit, cursor);
      cursor = retrievedSecretsAndCursor.decodedCursor();

      List<SanitizedSecretWithGroups> secrets = retrievedSecretsAndCursor.secrets();
      assertThat(secrets).isNotNull();
      if (limit != null) {
        assertThat(secrets.size()).isLessThanOrEqualTo(limit);
      }

      allRetrievedSecrets.addAll(secrets);
    } while (cursor != null);
    return allRetrievedSecrets;
  }

  private long createSecret(CreateSecretRequestV2 createSecretRequest) {
    long seriesId = secretSeriesDAO.createSecretSeries(createSecretRequest.name(),
        null, "creator", createSecretRequest.description(), createSecretRequest.type(), null, now);
    long contentId = secretContentDAO.createSecretContent(seriesId,
        createSecretRequest.content(), "checksum", "creator", createSecretRequest.metadata(),
        createSecretRequest.expiry(), now);
    secretSeriesDAO.setCurrentVersion(seriesId, contentId, "creator", now);
    return seriesId;
  }

  private void assertListContainsSecretsWithIds(List<SanitizedSecretWithGroups> secrets, List<Long> ids) {
    List<Long> foundIds = new ArrayList<>();
    for (SanitizedSecretWithGroups secretWithGroups : secrets) {
      if (ids.contains(secretWithGroups.secret().id())) {
        foundIds.add(secretWithGroups.secret().id());
      }
    }
    assertThat(foundIds).as("List should contain secrets with IDs %s; found IDs %s in secret list %s", ids, foundIds, secrets)
        .containsExactlyElementsOf(ids);
  }

  private void assertListDoesNotContainSecretsWithIds(List<SanitizedSecretWithGroups> secrets, List<Long> ids) {
    Set<Long> foundIds = new HashSet<>();
    for (SanitizedSecretWithGroups secretWithGroups : secrets) {
      if (ids.contains(secretWithGroups.secret().id())) {
        foundIds.add(secretWithGroups.secret().id());
      }
    }
    assertThat(foundIds).as("List should NOT contain secrets with IDs %s; found IDs %s in secret list %s", ids, foundIds, secrets)
        .isEmpty();
  }
}
