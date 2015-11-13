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
import java.time.OffsetDateTime;
import java.util.List;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.service.config.ShadowWrite;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import keywhiz.shadow_write.jooq.tables.Secrets;
import keywhiz.shadow_write.jooq.tables.SecretsContent;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.tools.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class SecretContentDAOTest {
  @Inject DSLContext jooqContext;
  @Inject @ShadowWrite DSLContext jooqShadowWriteContext;

  @Inject SecretContentDAOFactory secretContentDAOFactory;

  final static ApiDate date = ApiDate.now();
  ImmutableMap<String, String> metadata = ImmutableMap.of("foo", "bar");

  SecretContent secretContent1 = SecretContent.of(11, 22, "[crypted]", "", date, "creator", date,
      "creator", metadata);

  SecretContentDAO secretContentDAO;

  @Before
  public void setUp() throws Exception {
    secretContentDAO = secretContentDAOFactory.readwrite();
    long now = OffsetDateTime.now().toEpochSecond();

    jooqContext.insertInto(SECRETS, SECRETS.ID, SECRETS.NAME, SECRETS.CREATEDAT,
        SECRETS.UPDATEDAT)
        .values(secretContent1.secretSeriesId(), "secretName", now, now)
        .execute();

    jooqContext.insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, secretContent1.id())
        .set(SECRETS_CONTENT.SECRETID, secretContent1.secretSeriesId())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secretContent1.encryptedContent())
        .set(SECRETS_CONTENT.VERSION, secretContent1.version().orElse(null))
        .set(SECRETS_CONTENT.CREATEDAT, secretContent1.createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.CREATEDBY, secretContent1.createdBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secretContent1.updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secretContent1.updatedBy())
        .set(SECRETS_CONTENT.METADATA, JSONObject.toJSONString(secretContent1.metadata()))
        .execute();

    try {
      jooqShadowWriteContext.truncate(Secrets.SECRETS).execute();
    } catch (DataAccessException e) {}
    try {
      jooqShadowWriteContext.truncate(SecretsContent.SECRETS_CONTENT).execute();
    } catch (DataAccessException e) {}
    jooqShadowWriteContext.insertInto(Secrets.SECRETS, Secrets.SECRETS.ID, Secrets.SECRETS.NAME,
        Secrets.SECRETS.CREATEDAT, Secrets.SECRETS.UPDATEDAT)
        .values(secretContent1.secretSeriesId(), "secretName", secretContent1.createdAt().toEpochSecond(), secretContent1.updatedAt().toEpochSecond())
        .execute();
  }

  @Test public void createSecretContent() {
    int before = tableSize();
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted", "version",
        "creator", metadata);
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted2", "version2",
        "creator", metadata);
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted3", "version3",
        "creator", metadata);
    assertThat(tableSize()).isEqualTo(before + 3);

    // Check that shadow write worked.
    Object[][] r = jooqShadowWriteContext
        .select(SecretsContent.SECRETS_CONTENT.SECRETID, SecretsContent.SECRETS_CONTENT.VERSION)
        .from(SecretsContent.SECRETS_CONTENT)
        .fetchArrays();
    assertThat(r.length).isEqualTo(3);
    assertThat(r[0]).containsOnly(secretContent1.secretSeriesId(), "version");
    assertThat(r[1]).containsOnly(secretContent1.secretSeriesId(), "version2");
    assertThat(r[2]).containsOnly(secretContent1.secretSeriesId(), "version3");
  }

  @Test public void getSecretContentById() {
    assertThat(secretContentDAO.getSecretContentById(secretContent1.id())).contains(secretContent1);
  }

  @Test public void getSecretContentsBySecretId() {
    long id1 = secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted", "version", "creator",
        metadata);
    long id2 = secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted2", "version2", "creator",
        metadata);
    long id3 = secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted3", "version3", "creator",
        metadata);

    List<Long> actualIds = secretContentDAO.getSecretContentsBySecretId(secretContent1.secretSeriesId())
        .stream()
        .map((content) -> (content == null) ? 0 : content.id())
        .collect(toList());

    assertThat(actualIds).containsExactly(secretContent1.id(), id1, id2, id3);
  }

  @Test public void getVersionsBySecretId() {
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted", "version", "creator",
        metadata);
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted2", "version2", "creator",
        metadata);
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId(), "encrypted3", "version3", "creator",
        metadata);

    // We have the empty string as a version from the setUp() call
    assertThat(secretContentDAO.getVersionFromSecretId(secretContent1.secretSeriesId()))
        .hasSameElementsAs(ImmutableList.of("", "version", "version2", "version3"));
  }

  private int tableSize() {
    return jooqContext.fetchCount(SECRETS_CONTENT);
  }
}
