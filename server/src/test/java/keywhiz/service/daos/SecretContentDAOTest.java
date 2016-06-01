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
import java.util.List;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretContent;
import keywhiz.service.daos.SecretContentDAO.SecretContentDAOFactory;
import org.jooq.DSLContext;
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
  @Inject SecretContentDAOFactory secretContentDAOFactory;

  final static ApiDate date = ApiDate.now();
  ImmutableMap<String, String> metadata = ImmutableMap.of("foo", "bar");

  SecretContent secretContent1 = SecretContent.of(11, 22, "[crypted]", date, "creator", date,
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
        .set(SECRETS_CONTENT.CREATEDAT, secretContent1.createdAt().toEpochSecond())
        .set(SECRETS_CONTENT.CREATEDBY, secretContent1.createdBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secretContent1.updatedAt().toEpochSecond())
        .set(SECRETS_CONTENT.UPDATEDBY, secretContent1.updatedBy())
        .set(SECRETS_CONTENT.METADATA, JSONObject.toJSONString(secretContent1.metadata()))
        .execute();
  }

  @Test public void createSecretContent() {
    int before = tableSize();
    secretContentDAO.createSecretContent(secretContent1.secretSeriesId()+1, "encrypted", "creator",
        metadata, 0);
    assertThat(tableSize()).isEqualTo(before + 1);
  }

  @Test public void getSecretContentById() {
    assertThat(secretContentDAO.getSecretContentById(secretContent1.id())).contains(secretContent1);
  }

  @Test public void getSecretContentsBySecretId() {
    List<Long> actualIds = secretContentDAO.getSecretContentsBySecretId(secretContent1.secretSeriesId())
        .stream()
        .map((content) -> (content == null) ? 0 : content.id())
        .collect(toList());

    assertThat(actualIds).containsExactly(secretContent1.id());
  }

  private int tableSize() {
    return jooqContext.fetchCount(SECRETS_CONTENT);
  }
}
