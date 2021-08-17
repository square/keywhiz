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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.UUID;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.automation.v2.PartialUpdateSecretRequestV2;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.config.Readwrite;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoFixtures;
import keywhiz.service.crypto.RowHmacGenerator;
import org.joda.time.DateTime;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(KeywhizTestRunner.class)
public class SecretDAOTest {
  private static final ContentCryptographer cryptographer = CryptoFixtures.contentCryptographer();
  private static final ApiDate date = ApiDate.now();
  private static final ImmutableMap<String, String> NO_METADATA = ImmutableMap.of();

  @Inject private DSLContext jooqContext;
  @Inject private ObjectMapper objectMapper;
  @Inject @Readwrite private SecretDAO secretDAO;
  @Inject @Readwrite private GroupDAO groupDAO;
  @Inject private RowHmacGenerator rowHmacGenerator;

  private ImmutableMap<String, String> emptyMetadata = ImmutableMap.of();

  private SecretSeries series1 =
      SecretSeries.of(1, "secret1", null, "desc1", date, "creator", date, "updater", null, null, 101L);
  private String content = "c2VjcmV0MQ==";
  private String encryptedContent =
      cryptographer.encryptionKeyDerivedFrom(series1.name()).encrypt(content);
  private SecretContent content1 =
      SecretContent.of(101, 1, encryptedContent, "checksum", date, "creator", date, "updater",
          emptyMetadata, 0);
  private SecretSeriesAndContent secret1 = SecretSeriesAndContent.of(series1, content1);

  private SecretSeries series2 =
      SecretSeries.of(2, "secret2", null, "desc2", date, "creator", date, "updater", null, null, 103L);
  private SecretContent content2a =
      SecretContent.of(102, 2, encryptedContent, "checksum", date, "creator", date, "updater",
          emptyMetadata, 0);
  private SecretSeriesAndContent secret2a = SecretSeriesAndContent.of(series2, content2a);

  private SecretContent content2b =
      SecretContent.of(103, 2, "some other content", "checksum", date, "creator", date, "updater",
          emptyMetadata, 0);
  private SecretSeriesAndContent secret2b = SecretSeriesAndContent.of(series2, content2b);

  private SecretSeries series3 =
      SecretSeries.of(3, "secret3", null, "desc3", date, "creator", date, "updater", null, null, null);
  private SecretContent content3 =
      SecretContent.of(104, 3, encryptedContent, "checksum", date, "creator", date, "updater",
          emptyMetadata, 0);
  private SecretSeriesAndContent secret3 = SecretSeriesAndContent.of(series3, content3);

  @Before
  public void setUp() throws Exception {
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
        .set(SECRETS_CONTENT.ROW_HMAC, rowHmacGenerator.computeRowHmac(SECRETS_CONTENT.getName(),
            List.of(secret1.content().encryptedContent(),
                    objectMapper.writeValueAsString(secret1.content().metadata()),
                    secret1.content().id())
        ))
        .execute();

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
        .set(SECRETS_CONTENT.ROW_HMAC, rowHmacGenerator.computeRowHmac(SECRETS_CONTENT.getName(),
            List.of(secret2a.content().encryptedContent(),
                    objectMapper.writeValueAsString(secret2a.content().metadata()),
                    secret2a.content().id())
        ))
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
        .set(SECRETS_CONTENT.ROW_HMAC, rowHmacGenerator.computeRowHmac(SECRETS_CONTENT.getName(),
            List.of(secret2b.content().encryptedContent(),
                    objectMapper.writeValueAsString(secret2b.content().metadata()),
                    secret2b.content().id())
        ))
        .execute();

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
        .set(SECRETS_CONTENT.ROW_HMAC, rowHmacGenerator.computeRowHmac(SECRETS_CONTENT.getName(),
            List.of(secret3.content().encryptedContent(),
                    objectMapper.writeValueAsString(secret3.content().metadata()),
                    secret3.content().id())
        ))
        .execute();
  }

  //---------------------------------------------------------------------------------------
  // createSecret
  //---------------------------------------------------------------------------------------

  @Test public void failsToCreateSecretWithNonExistingOwner() {
    assertThrows(IllegalArgumentException.class, () -> createSecretWithOwner(randomName()));
  }

  @Test public void createsSecretWithOwner() {
    String ownerName = createGroup();
    long secretId = createSecretWithOwner(ownerName);

    SecretSeriesAndContent secret = secretDAO.getSecretById(secretId).get();
    assertEquals(ownerName, secret.series().owner());
  }

  @Test
  public void createOrUpdateNonExistingSecretWithOwner() {
    String ownerName = createGroup();
    long secretId = createOrUpdateSecretWithOwner(ownerName);

    SecretSeriesAndContent secret = secretDAO.getSecretById(secretId).get();
    assertEquals(ownerName, secret.series().owner());
  }

  @Test
  public void createOrUpdateExistingSecretDoesNotChangeOwner() {
    String ownerName1 = createGroup();
    String ownerName2 = createGroup();

    String secretName = randomName();

    long secretId1 = createOrUpdateSecretWithOwner(secretName, ownerName1);
    long secretId2 = createOrUpdateSecretWithOwner(secretName, ownerName2);

    assertEquals(secretId1, secretId2);

    SecretSeriesAndContent secret = secretDAO.getSecretById(secretId1).get();
    assertEquals(ownerName1, secret.series().owner());
  }

  @Test public void createSecret() {
    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    String name = "newSecret";
    String content = "c2VjcmV0MQ==";
    String hmac = cryptographer.computeHmac(content.getBytes(UTF_8), "hmackey");
    String encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    long newId = secretDAO.createSecret(name, null, encryptedContent, hmac, "creator",
        ImmutableMap.of(), 0, "", null, ImmutableMap.of());
    SecretSeriesAndContent newSecret = secretDAO.getSecretById(newId).get();

    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore + 1);
    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore + 1);

    newSecret = secretDAO.getSecretByName(newSecret.series().name()).get();
    assertThat(secretDAO.getSecrets(null, null, null,null, null)).containsOnly(secret1, secret2b, newSecret);
  }

  @Test(expected = DataAccessException.class)
  public void createSecretFailsIfSecretExists() {
    String name = "newSecret";
    secretDAO.createSecret(name, null, "some secret", "checksum", "creator", ImmutableMap.of(), 0, "",
        null, ImmutableMap.of());
    secretDAO.createSecret(name, null, "some secret", "checksum", "creator", ImmutableMap.of(), 0, "",
        null, ImmutableMap.of());
  }

  @Test(expected = BadRequestException.class)
  public void createSecretFailsIfNameHasLeadingPeriod() {
    String name = ".newSecret";
    secretDAO.createSecret(name, null, "some secret", "checksum", "creator", ImmutableMap.of(), 0, "",
        null, ImmutableMap.of());
  }

  @Test(expected = BadRequestException.class)
  public void createSecretFailsIfNameIsTooLong() {
    String name =
        "newSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecretnewSecret";
    secretDAO.createSecret(name, null, "some secret", "checksum", "creator", ImmutableMap.of(), 0, "",
        null, ImmutableMap.of());
  }

  @Test(expected = DataAccessException.class)
  public void createSecretFailsIfNameMatchesDeletedSecret() {
    String name = "newSecret";
    long firstId = secretDAO.createSecret(name, null, "content1",
        cryptographer.computeHmac("content1".getBytes(UTF_8), "hmackey"), "creator1",
        ImmutableMap.of("foo", "bar"), 1000, "description1", "type1", ImmutableMap.of());

    // When a secret is deleted, its name should be changed.  However, if the name is not changed
    // for some reason or a name matching the altered name is used, secret creation will fail.
    jooqContext.update(SECRETS)
        .set(SECRETS.CURRENT, (Long) null)
        .where(SECRETS.ID.eq(firstId))
        .execute();

     secretDAO.createSecret(name, null, "content2",
         cryptographer.computeHmac("content2".getBytes(UTF_8), "hmackey"), "creator2",
        ImmutableMap.of("foo2", "bar2"), 2000, "description2", "type2", ImmutableMap.of());
  }

  //---------------------------------------------------------------------------------------
  // createOrUpdateSecret
  //---------------------------------------------------------------------------------------

  @Test public void createOrUpdateSecretWhenSecretDoesNotExist() {
    int secretsBefore = tableSize(SECRETS);
    int secretContentsBefore = tableSize(SECRETS_CONTENT);

    String name = "newSecret";
    String content = "c2VjcmV0MQ==";
    String hmac = cryptographer.computeHmac(content.getBytes(UTF_8), "hmackey");
    String encryptedContent = cryptographer.encryptionKeyDerivedFrom(name).encrypt(content);
    long newId = secretDAO.createOrUpdateSecret(name, null, encryptedContent, hmac, "creator",
        ImmutableMap.of(), 0, "", null, ImmutableMap.of());
    SecretSeriesAndContent newSecret = secretDAO.getSecretById(newId).get();

    assertThat(tableSize(SECRETS)).isEqualTo(secretsBefore + 1);
    assertThat(tableSize(SECRETS_CONTENT)).isEqualTo(secretContentsBefore + 1);

    newSecret = secretDAO.getSecretByName(newSecret.series().name()).get();
    assertThat(secretDAO.getSecrets(null, null, null, null, null)).containsOnly(secret1, secret2b, newSecret);
  }

  @Test public void createOrUpdateSecretWhenSecretExists() {
    String name = "newSecret";
    long firstId = secretDAO.createSecret(name, null, "content1",
        cryptographer.computeHmac("content1".getBytes(UTF_8), "hmackey"), "creator1",
        ImmutableMap.of("foo", "bar"), 1000, "description1", "type1", ImmutableMap.of());

    long secondId = secretDAO.createOrUpdateSecret(name, null, "content2",
        cryptographer.computeHmac("content2".getBytes(UTF_8), "hmackey"), "creator2",
        ImmutableMap.of("foo2", "bar2"), 2000, "description2", "type2", ImmutableMap.of());
    assertThat(secondId).isEqualTo(firstId);

    SecretSeriesAndContent newSecret = secretDAO.getSecretById(firstId).get();
    assertThat(newSecret.series().createdBy()).isEqualTo("creator1");
    assertThat(newSecret.series().updatedBy()).isEqualTo("creator2");
    assertThat(newSecret.series().description()).isEqualTo("description2");
    assertThat(newSecret.series().type().get()).isEqualTo("type2");
    assertThat(newSecret.content().createdBy()).isEqualTo("creator2");
    assertThat(newSecret.content().encryptedContent()).isEqualTo("content2");
    assertThat(newSecret.content().metadata()).isEqualTo(ImmutableMap.of("foo2", "bar2"));
  }

  //---------------------------------------------------------------------------------------
  // updateSecret
  //---------------------------------------------------------------------------------------

  @Test(expected = NotFoundException.class)
  public void partialUpdateSecretWhenSecretSeriesDoesNotExist() {
    String name = "newSecret";
    String content = "c2VjcmV0MQ==";

    PartialUpdateSecretRequestV2 request =
        PartialUpdateSecretRequestV2.builder().contentPresent(true).content(content).build();

    secretDAO.partialUpdateSecret(name, "test", request);
  }

  @Test(expected = NotFoundException.class)
  public void partialUpdateSecretWhenSecretContentDoesNotExist() {
    String name = "newSecret";
    String content = "c2VjcmV0MQ==";

    PartialUpdateSecretRequestV2 request =
        PartialUpdateSecretRequestV2.builder().contentPresent(true).content(content).build();

    jooqContext.insertInto(SECRETS)
        .set(SECRETS.ID, 12L)
        .set(SECRETS.NAME, name)
        .set(SECRETS.DESCRIPTION, series1.description())
        .set(SECRETS.CREATEDBY, series1.createdBy())
        .set(SECRETS.CREATEDAT, series1.createdAt().toEpochSecond())
        .set(SECRETS.UPDATEDBY, series1.updatedBy())
        .set(SECRETS.UPDATEDAT, series1.updatedAt().toEpochSecond())
        .set(SECRETS.CURRENT, 12L)
        .execute();

    secretDAO.partialUpdateSecret(name, "test", request);
  }

  @Test(expected = NotFoundException.class)
  public void partialUpdateSecretWhenSecretCurrentIsNotSet() {
    String content = "c2VjcmV0MQ==";

    PartialUpdateSecretRequestV2 request =
        PartialUpdateSecretRequestV2.builder().contentPresent(true).content(content).build();

    secretDAO.partialUpdateSecret(series3.name(), "test", request);
  }

  @Test public void partialUpdateSecretWhenSecretExists() {

    // Update the content and set the type for series1
    long id = secretDAO.partialUpdateSecret(series1.name(), "creator1",
        PartialUpdateSecretRequestV2.builder()
            .contentPresent(true)
            .content("content1")
            .typePresent(true)
            .type("type1")
            .build());

    SecretSeriesAndContent newSecret =
        secretDAO.getSecretById(id).orElseThrow(IllegalStateException::new);
    assertThat(newSecret.series().createdBy()).isEqualTo("creator");
    assertThat(newSecret.series().updatedBy()).isEqualTo("creator1");
    assertThat(newSecret.series().description()).isEqualTo(series1.description());
    assertThat(newSecret.series().type().get()).isEqualTo("type1");
    assertThat(newSecret.content().createdBy()).isEqualTo("creator1");
    assertThat(newSecret.content().hmac()).isEqualTo(
        cryptographer.computeHmac("content1".getBytes(UTF_8), "hmackey"));
    assertThat(newSecret.content().metadata()).isEqualTo(secret1.content().metadata());
    assertThat(newSecret.content().expiry()).isEqualTo(secret1.content().expiry());

    // Update the expiry and metadata for series2
    id = secretDAO.partialUpdateSecret(series2.name(), "creator2",
        PartialUpdateSecretRequestV2.builder()
            .expiryPresent(true)
            .expiry(12345L)
            .metadataPresent(true)
            .metadata(ImmutableMap.of("owner", "keywhiz-test"))
            .build());

    newSecret =
        secretDAO.getSecretById(id).orElseThrow(IllegalStateException::new);
    assertThat(newSecret.series().createdBy()).isEqualTo("creator");
    assertThat(newSecret.series().updatedBy()).isEqualTo("creator2");
    assertThat(newSecret.series().description()).isEqualTo(series2.description());
    assertThat(newSecret.content().createdBy()).isEqualTo("creator2");
    assertThat(newSecret.content().hmac()).isEqualTo("checksum");
    assertThat(newSecret.content().metadata()).isEqualTo(ImmutableMap.of("owner", "keywhiz-test"));
    assertThat(newSecret.content().expiry()).isEqualTo(12345L);
  }

  //---------------------------------------------------------------------------------------

  @Test public void getSecretByName() {
    String name = secret2b.series().name();
    assertThat(secretDAO.getSecretByName(name)).contains(secret2b);
  }

  @Test public void getSecretByNameOneReturnsEmptyWhenCurrentVersionIsNull() {
    String name = secret1.series().name();

    jooqContext.update(SECRETS)
        .set(SECRETS.CURRENT, (Long) null)
        .where(SECRETS.ID.eq(series1.id()))
        .execute();
    assertThat(secretDAO.getSecretByName(name)).isEmpty();
  }

  @Test public void getSecretByNameOneReturnsEmptyWhenRowIsMissing() {
    String name = "nonExistantSecret";
    assertThat(secretDAO.getSecretByName(name)).isEmpty();

    long newId = secretDAO.createSecret(name, null, "content",
        cryptographer.computeHmac("content".getBytes(UTF_8), "hmackey"), "creator", ImmutableMap.of(), 0, "",
        null, ImmutableMap.of());
    SecretSeriesAndContent newSecret = secretDAO.getSecretById(newId).get();

    assertThat(secretDAO.getSecretByName(name)).isPresent();

    jooqContext.deleteFrom(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.ID.eq(newSecret.content().id()))
        .execute();

    assertThat(secretDAO.getSecretByName(name)).isEmpty();
  }

  @Test public void getSecretById() {
    assertThat(secretDAO.getSecretById(series2.id())).isEqualTo(Optional.of(secret2b));
  }

  @Test public void getSecretByIdOneReturnsEmptyWhenCurrentVersionIsNull() {
    jooqContext.update(SECRETS)
        .set(SECRETS.CURRENT, (Long) null)
        .where(SECRETS.ID.eq(series2.id()))
        .execute();
    assertThat(secretDAO.getSecretById(series2.id())).isEmpty();
  }

  @Test(expected = IllegalStateException.class)
  public void getSecretByIdOneThrowsExceptionIfCurrentVersionIsInvalid() {
    jooqContext.update(SECRETS)
        .set(SECRETS.CURRENT, -1234L)
        .where(SECRETS.ID.eq(series2.id()))
        .execute();
    secretDAO.getSecretById(series2.id());
  }

  @Test public void getNonExistentSecret() {
    assertThat(secretDAO.getSecretByName("non-existent")).isEmpty();
    assertThat(secretDAO.getSecretById(-1231)).isEmpty();
  }

  @Test public void getSecrets() {
    assertThat(secretDAO.getSecrets(null, null, null, null, null)).containsOnly(secret1, secret2b);
  }

  @Test public void getSecretsByNameOnly() {
    assertThat(secretDAO.getSecretsNameOnly()).containsOnly(
        new SimpleEntry<>(series1.id(), series1.name()),
        new SimpleEntry<>(series2.id(), series2.name()));
  }

  @Test public void deleteSecretsByName() {
    secretDAO.createSecret("toBeDeleted_deleteSecretsByName", null, "encryptedShhh",
        cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeleted_deleteSecretsByName");

    Optional<SecretSeriesAndContent> secret =
        secretDAO.getSecretByName("toBeDeleted_deleteSecretsByName");
    assertThat(secret).isEmpty();
  }

  @Test public void deleteSecretsByNameAndRecreate() {
    secretDAO.createSecret("toBeDeletedAndReplaced", null, "encryptedShhh",
        cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeletedAndReplaced");

    Optional<SecretSeriesAndContent> secret = secretDAO.getSecretByName("toBeDeletedAndReplaced");
    assertThat(secret).isEmpty();

    secretDAO.createSecret("toBeDeletedAndReplaced", null, "secretsgohere",
        cryptographer.computeHmac("secretsgohere".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);
    secret = secretDAO.getSecretByName("toBeDeletedAndReplaced");
    assertThat(secret).isPresent();
  }

  @Test public void deleteSecretsByNameAndRecreateWithUpdate() {
    secretDAO.createSecret("toBeDeletedAndReplaced", null, "encryptedShhh",
        cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeletedAndReplaced");

    Optional<SecretSeriesAndContent> secret = secretDAO.getSecretByName("toBeDeletedAndReplaced");
    assertThat(secret).isEmpty();

    secretDAO.createOrUpdateSecret("toBeDeletedAndReplaced", null, "secretsgohere",
        cryptographer.computeHmac("secretsgohere".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);
    secret = secretDAO.getSecretByName("toBeDeletedAndReplaced");
    assertThat(secret).isPresent();

    // The old version of the secret should not be available
    Optional<ImmutableList<SanitizedSecret>> versions =
        secretDAO.getSecretVersionsByName("toBeDeletedAndReplaced", 0, 50);
    assertThat(versions).isPresent();
    assertThat(versions.get().size()).isEqualTo(1);
  }

  @Test public void undeleteSecret() {
    secretDAO.createSecret("toBeDeletedAndUndeleted", null, "encryptedShhh",
        cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    //Update secret so that there will be a new secrets content
    secretDAO.createOrUpdateSecret("toBeDeletedAndUndeleted", null, "secretsgohere",
        cryptographer.computeHmac("secretsgohere".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeletedAndUndeleted");

    Optional<SecretSeriesAndContent> undeleteSecret1 = secretDAO.getSecretByName("toBeDeletedAndUndeleted");
    assertThat(undeleteSecret1).isEmpty();

    secretDAO.createSecret("toBeDeletedAndUndeleted", null, "blah",
        cryptographer.computeHmac("blah".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeletedAndUndeleted");

    Optional<SecretSeriesAndContent> undeleteSecret2 = secretDAO.getSecretByName("toBeDeletedAndUndeleted");
    assertThat(undeleteSecret2).isEmpty();

    // The old version of the secret should not be available
    List<SecretSeries> oldDeletedSecrets =
        secretDAO.getSecretsWithDeletedName("toBeDeletedAndUndeleted");
    assertThat(oldDeletedSecrets.size()).isEqualTo(2);

    //Because secret Ids are generated randomly, we need to record both secret ids to find the
    //secret id with two secrets contents associated with it
    long secretId = oldDeletedSecrets.get(0).id();
    long otherSecretId = oldDeletedSecrets.get(1).id();

    Optional<ImmutableList<SecretSeriesAndContent>> secretContents =
        secretDAO.getDeletedSecretVersionsBySecretId(secretId, 0, 50);
    assertThat(secretContents.isPresent());

    //If secretId only has 1 secrets content associated, pick the other secret id and retrieve its secrets contents
    if(secretContents.get().size() == 1) {
      secretId = otherSecretId;
      secretContents = secretDAO.getDeletedSecretVersionsBySecretId(secretId, 0, 50);
    }

    assertThat(secretContents.get().size()).isEqualTo(2);

    //Arbitrarily pick the 0th secrets content associated with secretId to use in undeleting
    long contentId = secretContents.get().get(0).content().id();

    assertThat(secretDAO.getSecretByName("toBeDeletedAndUndeleted")).isEmpty();
    secretDAO.setCurrentSecretVersionBySecretId(secretId, contentId, "updater");
    secretDAO.renameSecretById(secretId, "toBeDeletedAndUndeleted", "creator");
    Optional<SecretSeriesAndContent> undeletedSecretAndContent =
        secretDAO.getSecretByName("toBeDeletedAndUndeleted");
    assertThat(undeletedSecretAndContent).isPresent();
    assertThat(undeletedSecretAndContent.get().content().id()).isEqualTo(contentId);
  }

  @Test public void undeleteSecretWithExistingSecretHavingDeletedName() {
    secretDAO.createSecret("toBeDeletedAndUndeleted", null, "encryptedShhh",
        cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    //Update secret so that there will be a new secrets content
    secretDAO.createOrUpdateSecret("toBeDeletedAndUndeleted", null, "secretsgohere",
        cryptographer.computeHmac("secretsgohere".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeletedAndUndeleted");

    Optional<SecretSeriesAndContent> undeleteSecret1 = secretDAO.getSecretByName("toBeDeletedAndUndeleted");
    assertThat(undeleteSecret1).isEmpty();

    secretDAO.createSecret("toBeDeletedAndUndeleted", null, "blah",
        cryptographer.computeHmac("blah".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    secretDAO.deleteSecretsByName("toBeDeletedAndUndeleted");

    Optional<SecretSeriesAndContent> undeleteSecret2 = secretDAO.getSecretByName("toBeDeletedAndUndeleted");
    assertThat(undeleteSecret2).isEmpty();

    //Don't delete this secret so that when undeleting undeleteSecret1 later, we have to rename undeleteSecret1
    secretDAO.createSecret("toBeDeletedAndUndeleted", null, "blah",
        cryptographer.computeHmac("blah".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    Optional<SecretSeriesAndContent> undeleteSecret3 = secretDAO.getSecretByName("toBeDeletedAndUndeleted");
    assertThat(undeleteSecret3).isPresent();

    List<SecretSeries> oldDeletedSecrets =
        secretDAO.getSecretsWithDeletedName("toBeDeletedAndUndeleted");
    assertThat(oldDeletedSecrets.size()).isEqualTo(2);

    //Because secret Ids are generated randomly, we need to record both secret ids to find the
    //secret id with two secrets contents associated with it
    long secretId = oldDeletedSecrets.get(0).id();
    long otherSecretId = oldDeletedSecrets.get(1).id();
    Optional<ImmutableList<SecretSeriesAndContent>> secretContents =
        secretDAO.getDeletedSecretVersionsBySecretId(secretId, 0, 50);

    //If secretId only has 1 secrets content associated, pick the other secret id and retrieve its secrets contents
    if(secretContents.get().size() == 1) {
      secretId = otherSecretId;
      secretContents = secretDAO.getDeletedSecretVersionsBySecretId(secretId, 0, 50);
    }
    assertThat(secretContents.isPresent());
    assertThat(secretContents.get().size()).isEqualTo(2);

    //Arbitrarily pick the 0th secrets content associated with secretId to use in undeleting
    long contentId = secretContents.get().get(0).content().id();

    assertThat(secretDAO.getSecretByName("toBeDeletedAndUndeleted")).isPresent();
    secretDAO.setCurrentSecretVersionBySecretId(secretId, contentId, "creator");

    long secretIdFinal= secretId;
    assertThatThrownBy(() -> secretDAO.renameSecretById(secretIdFinal, "toBeDeletedAndUndeleted", "creator"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("name toBeDeletedAndUndeleted already used by an existing secret in keywhiz");

    //Because there is already an undeleted secret with the name "toBeDeletedAndUndeleted", we must
    //rename the deleted secret with a different name
    assertThat(secretDAO.getSecretByName("toBeDeletedAndUndeleted-2")).isEmpty();
    secretDAO.renameSecretById(secretId, "toBeDeletedAndUndeleted-2", "creator");
    Optional<SecretSeriesAndContent> undeletedSecretAndContent =
        secretDAO.getSecretByName("toBeDeletedAndUndeleted-2");
    assertThat(undeletedSecretAndContent).isPresent();
    assertThat(undeletedSecretAndContent.get().content().id()).isEqualTo(contentId);
  }

  //---------------------------------------------------------------------------------------
  // renameSecret
  //---------------------------------------------------------------------------------------
  @Test public void renameSecretById() {
    long secretId =
        secretDAO.createSecret("toBeRenamed_renameSecretByIdName", null, "encryptedShhh",
        cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
        ImmutableMap.of(), 0, "", null, null);

    Optional<SecretSeriesAndContent> secret = secretDAO.getSecretById(secretId);
    assertThat(secret.get().series().name()).isEqualTo("toBeRenamed_renameSecretByIdName");

    secretDAO.renameSecretById(secretId, "newName", "creator");

    secret = secretDAO.getSecretById(secretId);
    assertThat(secret).isPresent();
    assertThat(secret.get().series().name()).isEqualTo("newName");

    long secret2Id =
        secretDAO.createSecret("toBeRenamed_renameSecretByIdName2", null, "encryptedShhh",
            cryptographer.computeHmac("encryptedShhh".getBytes(UTF_8), "hmackey"), "creator",
            ImmutableMap.of(), 0, "", null, null);

    assertThatThrownBy(() -> secretDAO.renameSecretById(secret2Id,
        "newName", "creator"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("name newName already used by an existing secret in keywhiz");
  }
    //---------------------------------------------------------------------------------------
  // permanentlyRemoveSecret
  //---------------------------------------------------------------------------------------
  @Test
  public void countDeletedSecrets() {
    // one secret was deleted in the initial test setup
    assertThat(secretDAO.countDeletedSecrets()).isEqualTo(1);

    // delete a secret and recount
    secretDAO.deleteSecretsByName(series2.name());
    assertThat(secretDAO.countDeletedSecrets()).isEqualTo(2);
  }

  @Test
  public void countSecretsDeletedBeforeDate() {
    // some secrets may have been deleted in test setup
    int initialCount = secretDAO.countSecretsDeletedBeforeDate(DateTime.now().plusDays(30));

    // delete a secret and recount
    secretDAO.deleteSecretsByName(series2.name());
    assertThat(secretDAO.countSecretsDeletedBeforeDate(DateTime.now().plusDays(30))).isEqualTo(
        initialCount + 1);
  }

  @Test
  public void permanentlyRemoveSecret() throws Exception {
    // Initially, all secrets should be present in the database
    checkExpectedSecretSeriesInDatabase(ImmutableList.of(series1, series2, series3),
        ImmutableList.of());
    checkExpectedSecretContentsInDatabase(
        ImmutableList.of(content1, content2a, content2b, content3), ImmutableList.of());

    // After deleting the secret, the secrets should still be present in the database
    // (though the series will have current = null)
    secretDAO.deleteSecretsByName(series2.name());
    checkExpectedSecretSeriesInDatabase(ImmutableList.of(series1, series2, series3),
        ImmutableList.of());
    checkExpectedSecretContentsInDatabase(
        ImmutableList.of(content1, content2a, content2b, content3), ImmutableList.of());

    // This method does not validate whether the date is in the future, though the command does
    secretDAO.dangerPermanentlyRemoveSecretsDeletedBeforeDate(DateTime.now().plusDays(30), 0);

    // series2 and series3's associated information should be missing from the database.
    checkExpectedSecretSeriesInDatabase(ImmutableList.of(series1),
        ImmutableList.of(series2, series3));
    checkExpectedSecretContentsInDatabase(ImmutableList.of(content1),
        ImmutableList.of(content2a, content2b, content3));
  }

  /**
   * Verify that the given sets of secrets are present/not present in the database.
   */
  private void checkExpectedSecretSeriesInDatabase(List<SecretSeries> expectedPresentSeries,
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
  private void checkExpectedSecretContentsInDatabase(List<SecretContent> expectedPresentContents,
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

  private int tableSize(Table table) {
    return jooqContext.fetchCount(table);
  }

  // Testing batch secrets

  @Test public void getSecretsByName() {
    List<String> secrets = List.of(secret1.series().name(), secret2b.series().name());
    List<SecretSeriesAndContent> response = secretDAO.getSecretsByName(secrets);

    assertThat(response.size()).isEqualTo(2);
    assertThat(response).contains(secret1);
    assertThat(response).contains(secret2b);
  }

  @Test public void getNonExistantSecretsByName() {
    List<String> secrets = List.of("notasecret", "alsonotasecret");
    List<SecretSeriesAndContent> response = secretDAO.getSecretsByName(secrets);

    assertThat(response.size()).isEqualTo(0);
  }

  // Request duplicate, receive no duplicates

  @Test public void getDuplicateSecretsByName() {
    List<String> secrets = List.of(secret1.series().name(), secret2b.series().name(), secret1.series().name());
    List<SecretSeriesAndContent> response = secretDAO.getSecretsByName(secrets);

    assertThat(response.size()).isEqualTo(2);
    assertThat(response).contains(secret1);
    assertThat(response).contains(secret2b);
  }

  private String createGroup() {
    String name = randomName();
    groupDAO.createGroup(name, "creator", "description", NO_METADATA);
    return name;
  }

  private long createSecretWithOwner(String ownerName) {
    return secretDAO.createSecret(
        randomName(),
        ownerName,
        "encryptedSecret",
        "hmac",
        "creator",
        NO_METADATA,
        0,
        "description",
        null,
        null);
  }

  private long createOrUpdateSecretWithOwner(String ownerName) {
    return createOrUpdateSecretWithOwner(randomName(), ownerName);
  }

  private long createOrUpdateSecretWithOwner(String secretName, String ownerName) {
    return secretDAO.createOrUpdateSecret(
        secretName,
        ownerName,
        "encryptedSecret",
        "hmac",
        "creator",
        NO_METADATA,
        0,
        "description",
        null,
        null);
  }

  private static String randomName() {
    return UUID.randomUUID().toString();
  }
}
