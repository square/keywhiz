package keywhiz.service.daos;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.automation.v2.CreateSecretRequestV2;
import keywhiz.api.model.SanitizedSecretWithGroups;
import keywhiz.api.model.SanitizedSecretWithGroupsListAndCursor;
import keywhiz.api.model.SecretRetrievalCursor;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

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
  @Inject SecretSeriesDAO.SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Inject SecretContentDAO.SecretContentDAOFactory secretContentDAOFactory;
  @Inject SecretController secretController;

  SecretSeriesDAO secretSeriesDAO;
  SecretContentDAO secretContentDAO;

  @Before public void setUp() {
    secretSeriesDAO = secretSeriesDAOFactory.readwrite();
    secretContentDAO = secretContentDAOFactory.readwrite();

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
        "creator", createSecretRequest.description(), createSecretRequest.type(), null, now);
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
