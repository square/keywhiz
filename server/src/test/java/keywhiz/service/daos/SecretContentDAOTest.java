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

import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.service.crypto.RowHmacGenerator;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import org.jooq.DSLContext;
import org.jooq.tools.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static keywhiz.service.daos.SecretContentDAO.PRUNE_CUTOFF_ITEMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@RunWith(KeywhizTestRunner.class)
public class SecretContentDAOTest {
  @Inject DSLContext jooqContext;
  @Inject SecretContentDAOFactory secretContentDAOFactory;
  @Inject private RowHmacGenerator rowHmacGenerator;

  final static ApiDate date = ApiDate.now();
  ImmutableMap<String, String> metadata = ImmutableMap.of("foo", "bar");

  SecretContent secretContent1 = SecretContent.of(11, 22, "[crypted]", "checksum", date, "creator", date,
      "creator", metadata, 1136214245);

  SecretContentDAO secretContentDAO;

  @Before
  public void setUp() throws Exception {
    secretContentDAO = secretContentDAOFactory.readwrite();
    long now = OffsetDateTime.now().toEpochSecond();

    jooqContext.insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
        .values(secretContent1.secretSeriesId(), "secretName", now, now)
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, secretContent1.id())
        .set(SECRETS_CONTENT.SECRETID, secretContent1.secretSeriesId())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secretContent1.encryptedContent())
        .set(SECRETS_CONTENT.CONTENT_HMAC, "checksum")
        .set(SECRETS_CONTENT.CREATEDAT, secretContent1.createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.CREATEDBY, secretContent1.createdBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secretContent1.updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secretContent1.updatedBy())
        .set(SECRETS_CONTENT.METADATA, JSONObject.toJSONString(secretContent1.metadata()))
        .set(SECRETS_CONTENT.EXPIRY, 1136214245L)
        .set(SECRETS_CONTENT.ROW_HMAC, rowHmacGenerator.computeRowHmac(SECRETS_CONTENT.getName(),
            secretContent1.encryptedContent(), JSONObject.toJSONString(secretContent1.metadata()),
            secretContent1.id()))
        .execute();
  }

  @Test public void createSecretContent() {
    int before = tableSize();
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId()+1, "encrypted", "checksum", "creator",
        metadata, 1136214245, OffsetDateTime.now().toEpochSecond());
    assertThat(tableSize()).isEqualTo(before + 1);
  }

  @Test public void pruneOldContents() throws Exception {
    long now = OffsetDateTime.now().toEpochSecond();
    long secretSeriesId = 666;

    jooqContext.insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
        .values(secretSeriesId, "secretForPruneTest1", now, now)
        .execute();

    int before = tableSize();

    // Create contents
    long[] ids = new long[15];
    for (int i = 0; i < ids.length; i++) {
      long id = secretContentDAO.createSecretContent(secretSeriesId, "encrypted", "checksum",
          "creator", metadata, 1136214245, i);
      ids[i] = id;
    }

    assertThat(tableSize()).isEqualTo(before + ids.length);

    // Make most recent id be the current version for the secret series and prune
    jooqContext.update(SECRETS)
        .set(SECRETS.CURRENT, ids[ids.length-1])
        .where(SECRETS.ID.eq(secretSeriesId))
        .execute();

    secretContentDAO.pruneOldContents(secretSeriesId);

    // Last ten secrets in series should have survived (plus the current one)
    assertThat(tableSize()).isEqualTo(before + PRUNE_CUTOFF_ITEMS + 1);

    for (int i = 0; i < (ids.length - PRUNE_CUTOFF_ITEMS - 1); i++) {
      assertThat(secretContentDAO.getSecretContentById(ids[i])).isEmpty();
    }
    for (int i = (ids.length - PRUNE_CUTOFF_ITEMS - 1); i < ids.length; i++) {
      assertThat(secretContentDAO.getSecretContentById(ids[i])).isPresent();
    }

    // Other secrets contents left intact
    assertThat(secretContentDAO.getSecretContentById(secretContent1.id())).isPresent();
  }

  @Test public void pruneIgnores45DaysOrLess() throws Exception {
    long now = OffsetDateTime.now().toEpochSecond();
    long secretSeriesId = 666;

    jooqContext.insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
        .values(secretSeriesId, "secretForPruneTest2", now, now)
        .execute();

    int before = tableSize();

    // Create contents
    long[] ids = new long[15];
    for (int i = 0; i < ids.length; i++) {
      long id = secretContentDAO.createSecretContent(secretSeriesId, "encrypted",
          "checksum", "creator", metadata, 1136214245, now);
      ids[i] = id;
    }

    assertThat(tableSize()).isEqualTo(before + ids.length);

    // Make most recent id be the current version for the secret series and prune
    jooqContext.update(SECRETS)
        .set(SECRETS.CURRENT, ids[ids.length-1])
        .where(SECRETS.ID.eq(secretSeriesId))
        .execute();

    secretContentDAO.pruneOldContents(secretSeriesId);

    // Nothing pruned
    for (int i = 0; i < ids.length; i++) {
      assertThat(secretContentDAO.getSecretContentById(ids[i])).isPresent();
    }
  }

  @Test public void getSecretContentById() {
    assertThat(secretContentDAO.getSecretContentById(secretContent1.id())).contains(secretContent1);
  }

  @Test public void preventSwapSecretContent() {
    long now = OffsetDateTime.now().toEpochSecond();
    long validSeriesId = 666;
    long maliciousSeriesId = 667;
    String secretName = "validSecretForRowHmacTest";
    String encryptedSecret = "encrypted";

    // Create a valid secret
    jooqContext.insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
        .values(validSeriesId, secretName, now, now)
        .execute();
    secretContentDAO.createSecretContent(validSeriesId, encryptedSecret, "checksum",
        "creator", metadata, 1136214245, now);

    // Delete valid secret series
    jooqContext.deleteFrom(SECRETS)
        .where(SECRETS.ID.eq(validSeriesId))
        .execute();
    // Steal valid secret series names
    jooqContext.insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT, SECRETS.UPDATEDAT)
        .values(maliciousSeriesId, secretName, now, now)
        .execute();

    long maliciousId = secretContentDAO.createSecretContent(maliciousSeriesId, "fake", "checksum",
        "creator", metadata, 1136214245, now);

    // Replace malicious secret_content with encrypted secret
    jooqContext.update(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, encryptedSecret)
        .where(SECRETS_CONTENT.ID.eq(maliciousId))
        .execute();

    assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
      secretContentDAO.getSecretContentById(maliciousId);
    }).withMessage(
        String.format("Secret Content HMAC verification failed for secretContent: %d", maliciousId));
  }

  private int tableSize() {
    return jooqContext.fetchCount(SECRETS_CONTENT);
  }
}
