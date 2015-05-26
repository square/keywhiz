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
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Optional;
import keywhiz.TestDBRule;
import keywhiz.api.model.SecretSeries;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.assertj.core.api.Assertions.assertThat;

public class SecretSeriesDAOTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  DSLContext dslContext;
  SecretSeriesDAO secretSeriesDAO;

  @Before
  public void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    dslContext = testDBRule.jooqContext();
    secretSeriesDAO = new SecretSeriesDAO(objectMapper);
  }

  @Test
  public void createAndLookupSecretSeries() {
    int before = tableSize();
    OffsetDateTime now = OffsetDateTime.now();

    long id = secretSeriesDAO.createSecretSeries(dslContext, "newSecretSeries", "creator", "desc",
        null, ImmutableMap.of("foo", "bar"));
    SecretSeries expected = new SecretSeries(id, "newSecretSeries", "desc", now, "creator", now,
        "creator", null, ImmutableMap.of("foo", "bar"));

    assertThat(tableSize()).isEqualTo(before + 1);

    SecretSeries actual = secretSeriesDAO.getSecretSeriesByName(dslContext, "newSecretSeries")
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
        "name", "description", "type", "generationOptions");

    actual = secretSeriesDAO.getSecretSeriesById(dslContext, id)
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
        "name", "description", "type", "generationOptions");
  }

  @Test
  public void deleteSecretSeriesByName() {
    secretSeriesDAO.createSecretSeries(dslContext, "toBeDeleted_deleteSecretSeriesByName",
        "creator", "", null, null);

    int secretsBefore = tableSize();

    secretSeriesDAO.deleteSecretSeriesByName(dslContext, "toBeDeleted_deleteSecretSeriesByName");

    assertThat(tableSize()).isEqualTo(secretsBefore - 1);

    Optional<SecretSeries> missingSecret =
        secretSeriesDAO.getSecretSeriesByName(dslContext, "toBeDeleted_deleteSecretSeriesByName");
    assertThat(missingSecret.isPresent()).isFalse();
  }

  @Test
  public void deleteSecretSeriesById() {
    long id = secretSeriesDAO.createSecretSeries(dslContext, "toBeDeleted_deleteSecretSeriesById",
        "creator", "", null, null);

    int secretsBefore = tableSize();

    secretSeriesDAO.deleteSecretSeriesById(dslContext, id);

    assertThat(tableSize()).isEqualTo(secretsBefore - 1);
    Optional<SecretSeries> missingSecret = secretSeriesDAO.getSecretSeriesById(dslContext, id);
    assertThat(missingSecret.isPresent()).isFalse();
  }

  @Test
  public void getNonExistentSecretSeries() {
    assertThat(secretSeriesDAO.getSecretSeriesByName(dslContext, "non-existent")
        .isPresent()).isFalse();
    assertThat(secretSeriesDAO.getSecretSeriesById(dslContext, -2328).isPresent()).isFalse();
  }

  private int tableSize() {
    return testDBRule.jooqContext().fetchCount(SECRETS);
  }
}
