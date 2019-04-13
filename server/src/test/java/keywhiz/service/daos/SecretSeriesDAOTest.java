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
import keywhiz.api.model.SecretSeries;
import keywhiz.service.daos.SecretSeriesDAO.SecretSeriesDAOFactory;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(KeywhizTestRunner.class)
public class SecretSeriesDAOTest {
  @Inject DSLContext jooqContext;
  @Inject SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Inject SecretContentDAO.SecretContentDAOFactory secretContentDAOFactory;

  SecretSeriesDAO secretSeriesDAO;

  @Before public void setUp() {
    secretSeriesDAO = secretSeriesDAOFactory.readwrite();
  }

  @Test public void createAndLookupSecretSeries() {
    int before = tableSize();
    long now = OffsetDateTime.now().toEpochSecond();
    ApiDate nowDate = new ApiDate(now);

    long id = secretSeriesDAO.createSecretSeries("newSecretSeries", "creator", "desc", null,
        ImmutableMap.of("foo", "bar"), now);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);

    SecretSeries expected = SecretSeries.of(id, "newSecretSeries", "desc", nowDate, "creator", nowDate,
        "creator", null, ImmutableMap.of("foo", "bar"), contentId);

    assertThat(tableSize()).isEqualTo(before + 1);

    SecretSeries actual = secretSeriesDAO.getSecretSeriesById(id)
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToIgnoringGivenFields(expected,"id");


    actual = secretSeriesDAO.getSecretSeriesByName("newSecretSeries")
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
        "name", "description", "type", "generationOptions", "currentVersion");
  }

  @Test public void setCurrentVersion() {
    long now = OffsetDateTime.now().toEpochSecond();

    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesByName", "creator",
        "", null, null, now);
    Optional<SecretSeries> secretSeriesById = secretSeriesDAO.getSecretSeriesById(id);
    assertThat(secretSeriesById.isPresent()).isFalse();

    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah",
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
    long id = secretSeriesDAO.createSecretSeries("someSecret", "creator", "",
        null, null, now);
    long other = secretSeriesDAO.createSecretSeries("someOtherSecret", "creator", "",
        null, null, now);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(other, "blah",
        "checksum", "creator", null, 0, now);

    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
  }

  @Test public void deleteSecretSeriesByName() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesByName", "creator",
        "", null, null, now);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName").get().currentVersion().isPresent()).isTrue();

    secretSeriesDAO.deleteSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName");
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName").isPresent()).isFalse();
    assertThat(secretSeriesDAO.getSecretSeriesById(id).isPresent()).isFalse();
  }

  @Test public void deleteSecretSeriesByNameAndRecreate() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeDeletedAndReplaced", "creator",
        "", null, null, now);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeletedAndReplaced").get().currentVersion().isPresent()).isTrue();

    secretSeriesDAO.deleteSecretSeriesByName("toBeDeletedAndReplaced");
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeletedAndReplaced").isPresent()).isFalse();
    assertThat(secretSeriesDAO.getSecretSeriesById(id).isPresent()).isFalse();

    id = secretSeriesDAO.createSecretSeries("toBeDeletedAndReplaced", "creator",
        "", null, null, now);
    contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah2",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeletedAndReplaced").get().currentVersion().isPresent()).isTrue();
  }

  @Test public void deleteSecretSeriesById() {
    long now = OffsetDateTime.now().toEpochSecond();
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesById",
        "creator", "", null, null, now);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah",
        "checksum", "creator", null, 0, now);
    secretSeriesDAO.setCurrentVersion(id, contentId, "creator", now);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).get().currentVersion().isPresent()).isTrue();

    secretSeriesDAO.deleteSecretSeriesById(id);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).isPresent()).isFalse();
  }

  @Test public void getNonExistentSecretSeries() {
    assertThat(secretSeriesDAO.getSecretSeriesByName("non-existent")).isEmpty();
    assertThat(secretSeriesDAO.getSecretSeriesById(-2328)).isEmpty();
  }

  private int tableSize() {
    return jooqContext.fetchCount(SECRETS);
  }
}
