package keywhiz.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoFixtures;
import keywhiz.service.daos.SecretDAO;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class DropDeletedSecretsCommandTest {
  @Inject private DSLContext jooqContext;
  @Inject private ObjectMapper objectMapper;
  @Inject private SecretDAO.SecretDAOFactory secretDAOFactory;

  private final static ContentCryptographer cryptographer = CryptoFixtures.contentCryptographer();
  private final static ApiDate january18 = ApiDate.parse("2018-01-01T00:00:00Z");
  private final static ApiDate january19 = ApiDate.parse("2019-01-01T00:00:00Z");
  private ImmutableMap<String, String> emptyMetadata = ImmutableMap.of();

  // an undeleted secret
  private SecretSeries series1 =
      SecretSeries.of(1, "secret1", "desc1", january18, "creator", january19, "updater", null, null,
          101L);
  private String content = "c2VjcmV0MQ==";
  private String encryptedContent =
      cryptographer.encryptionKeyDerivedFrom(series1.name()).encrypt(content);
  private SecretContent content1 =
      SecretContent.of(101, 1, encryptedContent, "checksum", january18, "creator", january18,
          "updater", emptyMetadata, 0);
  private SecretSeriesAndContent secret1 = SecretSeriesAndContent.of(series1, content1);

  // a secret deleted on 2018-01-01T00:00:00Z
  private SecretSeries series2 =
      SecretSeries.of(2, "secret2", "desc2", january18, "creator", january18, "updater", null, null,
          null);
  private SecretContent content2a =
      SecretContent.of(102, 2, encryptedContent, "checksum", january18, "creator", january18,
          "updater", emptyMetadata, 0);
  private SecretSeriesAndContent secret2a = SecretSeriesAndContent.of(series2, content2a);

  private SecretContent content2b =
      SecretContent.of(103, 2, "some other content", "checksum", january18, "creator", january18,
          "updater", emptyMetadata, 0);
  private SecretSeriesAndContent secret2b = SecretSeriesAndContent.of(series2, content2b);

  // a secret deleted on 2019-01-01T00:00:00Z
  private SecretSeries series3 =
      SecretSeries.of(3, "secret3", "desc3", january18, "creator", january19, "updater", null, null,
          null);
  private SecretContent content3 =
      SecretContent.of(104, 3, encryptedContent, "checksum", january18, "creator", january18,
          "updater", emptyMetadata, 0);
  private SecretSeriesAndContent secret3 = SecretSeriesAndContent.of(series3, content3);

  @Before
  public void setUp() throws Exception {
    // store an undeleted secret
    jooqContext.insertInto(SECRETS)
        .set(SECRETS.ID, series1.id())
        .set(SECRETS.NAME, series1.name())
        .set(SECRETS.DESCRIPTION, series1.description())
        .set(SECRETS.CREATEDBY, series1.createdBy())
        .set(SECRETS.CREATEDAT, series1.createdAt().toEpochSecond())
        .set(SECRETS.UPDATEDBY, series1.updatedBy())
        .set(SECRETS.UPDATEDAT, series1.updatedAt().toEpochSecond())
        .set(SECRETS.CURRENT, series1.currentVersion().orElse(null))
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, secret1.content().id())
        .set(SECRETS_CONTENT.SECRETID, secret1.series().id())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secret1.content().encryptedContent())
        .set(SECRETS_CONTENT.CONTENT_HMAC, "checksum")
        .set(SECRETS_CONTENT.CREATEDBY, secret1.content().createdBy())
        .set(SECRETS_CONTENT.CREATEDAT, secret1.content().createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secret1.content().updatedBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secret1.content().updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.METADATA,
            objectMapper.writeValueAsString(secret1.content().metadata()))
        .execute();

    // store a secret deleted on 2018-01-01T00:00:00Z
    jooqContext.insertInto(SECRETS)
        .set(SECRETS.ID, series2.id())
        .set(SECRETS.NAME, series2.name())
        .set(SECRETS.DESCRIPTION, series2.description())
        .set(SECRETS.CREATEDBY, series2.createdBy())
        .set(SECRETS.CREATEDAT, series2.createdAt().toEpochSecond())
        .set(SECRETS.UPDATEDBY, series2.updatedBy())
        .set(SECRETS.UPDATEDAT, series2.updatedAt().toEpochSecond())
        .set(SECRETS.CURRENT, series2.currentVersion().orElse(null))
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, secret2a.content().id())
        .set(SECRETS_CONTENT.SECRETID, secret2a.series().id())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secret2a.content().encryptedContent())
        .set(SECRETS_CONTENT.CONTENT_HMAC, "checksum")
        .set(SECRETS_CONTENT.CREATEDBY, secret2a.content().createdBy())
        .set(SECRETS_CONTENT.CREATEDAT, secret2a.content().createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secret2a.content().updatedBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secret2a.content().updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.METADATA,
            objectMapper.writeValueAsString(secret2a.content().metadata()))
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, secret2b.content().id())
        .set(SECRETS_CONTENT.SECRETID, secret2b.series().id())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secret2b.content().encryptedContent())
        .set(SECRETS_CONTENT.CONTENT_HMAC, "checksum")
        .set(SECRETS_CONTENT.CREATEDBY, secret2b.content().createdBy())
        .set(SECRETS_CONTENT.CREATEDAT, secret2b.content().createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secret2b.content().updatedBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secret2b.content().updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.METADATA,
            objectMapper.writeValueAsString(secret2b.content().metadata()))
        .execute();

    // store a secret deleted on 2019-01-01T00:00:00Z
    jooqContext.insertInto(SECRETS)
        .set(SECRETS.ID, series3.id())
        .set(SECRETS.NAME, series3.name())
        .set(SECRETS.DESCRIPTION, series3.description())
        .set(SECRETS.CREATEDBY, series3.createdBy())
        .set(SECRETS.CREATEDAT, series3.createdAt().toEpochSecond())
        .set(SECRETS.UPDATEDBY, series3.updatedBy())
        .set(SECRETS.UPDATEDAT, series3.updatedAt().toEpochSecond())
        .set(SECRETS.CURRENT, series3.currentVersion().orElse(null))
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, secret3.content().id())
        .set(SECRETS_CONTENT.SECRETID, secret3.series().id())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secret3.content().encryptedContent())
        .set(SECRETS_CONTENT.CONTENT_HMAC, "checksum")
        .set(SECRETS_CONTENT.CREATEDBY, secret3.content().createdBy())
        .set(SECRETS_CONTENT.CREATEDAT, secret3.content().createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secret3.content().updatedBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secret3.content().updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.METADATA,
            objectMapper.writeValueAsString(secret3.content().metadata()))
        .execute();
  }

  @Test
  public void testSecretDeletion_allDeletedSecrets() {
    runCommandWithConfirmationAndDate("yes", "2019-02-01T00:00:00Z");

    // check the database state; secret2 and secret3 should have been deleted
    checkExpectedSecretSeries(ImmutableList.of(series1), ImmutableList.of(series2, series3));
    checkExpectedSecretContents(ImmutableList.of(content1),
        ImmutableList.of(content2a, content2b, content3));
  }

  @Test
  public void testSecretDeletion_filterByDate_someSecretsRemoved() {
    runCommandWithConfirmationAndDate("yes", "2018-02-01T00:00:00Z");

    // check the database state; only secret2 should have been deleted
    checkExpectedSecretSeries(ImmutableList.of(series1, series3), ImmutableList.of(series2));
    checkExpectedSecretContents(ImmutableList.of(content1, content3),
        ImmutableList.of(content2a, content2b));
  }

  @Test
  public void testSecretDeletion_filterByDate_noSecretsRemoved() {
    runCommandWithConfirmationAndDate("yes", "2017-02-01T00:00:00Z");

    // check the database state; no secrets should have been deleted
    checkExpectedSecretSeries(ImmutableList.of(series1, series2, series3), ImmutableList.of());
    checkExpectedSecretContents(ImmutableList.of(content1, content2a, content2b, content3),
        ImmutableList.of());
  }

  @Test
  public void testSecretDeletion_futureDate() {
    runCommandWithConfirmationAndDate("yes", "5000-02-01T00:00:00Z");

    // check the database state; secrets should NOT have been deleted
    checkExpectedSecretSeries(ImmutableList.of(series1, series2, series3), ImmutableList.of());
    checkExpectedSecretContents(ImmutableList.of(content1, content2a, content2b, content3),
        ImmutableList.of());
  }

  @Test
  public void testSecretDeletion_invalidDate() {
    runCommandWithConfirmationAndDate("yes", "notadate");

    // check the database state; secrets should NOT have been deleted
    checkExpectedSecretSeries(ImmutableList.of(series1, series2, series3), ImmutableList.of());
    checkExpectedSecretContents(ImmutableList.of(content1, content2a, content2b, content3),
        ImmutableList.of());
  }

  @Test
  public void testSecretDeletion_noConfirmation() {
    runCommandWithConfirmationAndDate("no", "2019-02-01T00:00:00Z");

    // check the database state; secrets should NOT have been deleted
    checkExpectedSecretSeries(ImmutableList.of(series1, series2, series3), ImmutableList.of());
    checkExpectedSecretContents(ImmutableList.of(content1, content2a, content2b, content3),
        ImmutableList.of());
  }

  private void runCommandWithConfirmationAndDate(String confirmation, String date) {
    SecretDAO secretDAO = secretDAOFactory.readwrite();
    // confirm that the expected secrets are present
    checkExpectedSecretSeries(ImmutableList.of(series1, series2, series3), ImmutableList.of());
    checkExpectedSecretContents(ImmutableList.of(content1, content2a, content2b, content3),
        ImmutableList.of());

    DropDeletedSecretsCommand command = new DropDeletedSecretsCommand(secretDAO);

    // remove secrets
    InputStream in = new ByteArrayInputStream(confirmation.getBytes(UTF_8));
    System.setIn(in);
    command.run(null,
        new Namespace(ImmutableMap.of(DropDeletedSecretsCommand.INPUT_DELETED_BEFORE, date)),
        null);
    System.setIn(System.in);
  }

  /**
   * Verify that the given sets of secrets are present/not present in the database.
   */
  private void checkExpectedSecretSeries(List<SecretSeries> expectedPresentSeries,
      List<SecretSeries> expectedMissingSeries) {
    List<Long> secretSeriesIds = jooqContext.select(SECRETS.ID).from(SECRETS).fetch(SECRETS.ID);
    assertThat(secretSeriesIds).containsAll(
        expectedPresentSeries.stream().map(SecretSeries::id).collect(toList()));

    if (!expectedMissingSeries.isEmpty()) {
      assertThat(secretSeriesIds).doesNotContainAnyElementsOf(
          expectedMissingSeries.stream().map(SecretSeries::id).collect(toList()));
    }
  }

  /**
   * Verify that the given sets of secrets are present/not present in the database.
   */
  private void checkExpectedSecretContents(List<SecretContent> expectedPresentContents,
      List<SecretContent> expectedMissingContents) {
    List<Long> secretContentsIds =
        jooqContext.select(SECRETS_CONTENT.ID).from(SECRETS_CONTENT).fetch(SECRETS_CONTENT.ID);
    assertThat(secretContentsIds).containsAll(
        expectedPresentContents.stream().map(SecretContent::id).collect(toList()));

    if (!expectedMissingContents.isEmpty()) {
      assertThat(secretContentsIds).doesNotContainAnyElementsOf(
          expectedMissingContents.stream().map(SecretContent::id).collect(toList()));
    }
  }
}
