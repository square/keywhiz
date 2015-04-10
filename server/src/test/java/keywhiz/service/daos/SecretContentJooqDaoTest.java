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
import java.time.ZoneId;
import java.util.List;
import keywhiz.TestDBRule;
import keywhiz.api.model.SecretContent;
import org.jooq.tools.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Secrets.SECRETS;
import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretContentJooqDaoTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  final static OffsetDateTime date = OffsetDateTime.now(ZoneId.of("UTC"));
  ImmutableMap<String, String> metadata = ImmutableMap.of("foo", "bar");

  SecretContent secretContent1 = SecretContent.of(11, 22, "[crypted]", "", date, "creator", date,
      "creator", metadata);

  SecretContentJooqDao secretContentJooqDao;

  @Before
  public void setUp() throws Exception {
    secretContentJooqDao = new SecretContentJooqDao(testDBRule.jooqContext());

    testDBRule.jooqContext().delete(SECRETS).execute();
    testDBRule.jooqContext().insertInto(SECRETS, SECRETS.ID, SECRETS.NAME)
        .values((int) secretContent1.secretSeriesId(), "secretName")
        .execute();
    testDBRule.jooqContext().insertInto(SECRETS_CONTENT)
        .set(SECRETS_CONTENT.ID, (int) secretContent1.id())
        .set(SECRETS_CONTENT.SECRETID, (int) secretContent1.secretSeriesId())
        .set(SECRETS_CONTENT.ENCRYPTED_CONTENT, secretContent1.encryptedContent())
        .set(SECRETS_CONTENT.VERSION, secretContent1.version().orElse(null))
        .set(SECRETS_CONTENT.CREATEDAT, secretContent1.createdAt())
        .set(SECRETS_CONTENT.CREATEDBY, secretContent1.createdBy())
        .set(SECRETS_CONTENT.UPDATEDAT, secretContent1.updatedAt())
        .set(SECRETS_CONTENT.UPDATEDBY, secretContent1.updatedBy())
        .set(SECRETS_CONTENT.METADATA, JSONObject.toJSONString(secretContent1.metadata()))
        .execute();
  }

  @Test public void createSecretContent() {
    int before = tableSize();
    secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted", "version", "creator",
        metadata);
    secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted2", "version2", "creator",
        metadata);
    secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted3", "version3", "creator",
        metadata);
    assertThat(tableSize()).isEqualTo(before + 3);
  }

  @Test public void getSecretContentById() {
    SecretContent actualSecretContent = secretContentJooqDao.getSecretContentById(secretContent1.id())
        .orElseThrow(RuntimeException::new);
    assertThat(actualSecretContent).isEqualTo(secretContent1);
  }

  @Test public void getSecretContentsBySecretId() {
    long id1 = secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted", "version", "creator",
        metadata);
    long id2 = secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted2", "version2", "creator",
        metadata);
    long id3 = secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted3", "version3", "creator",
        metadata);

    List<Long> actualIds = secretContentJooqDao.getSecretContentsBySecretId(secretContent1.secretSeriesId())
        .stream()
        .map((content) -> (content == null) ? 0 : content.id())
        .collect(toList());

    assertThat(actualIds).containsExactly(secretContent1.id(), id1, id2, id3);
  }

  @Test public void getVersionsBySecretId() {
    secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted", "version", "creator",
        metadata);
    secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted2", "version2", "creator",
        metadata);
    secretContentJooqDao.createSecretContent(secretContent1.secretSeriesId(), "encrypted3", "version3", "creator",
        metadata);

    // We have the empty string as a version from the setUp() call
    assertThat(secretContentJooqDao.getVersionFromSecretId(secretContent1.secretSeriesId()))
        .hasSameElementsAs(ImmutableList.of("", "version", "version2", "version3"));
  }

  private int tableSize() {
    return testDBRule.jooqContext().fetchCount(SECRETS_CONTENT);
  }
}
