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
    ApiDate now = ApiDate.now();

    long id = secretSeriesDAO.createSecretSeries("newSecretSeries", "creator", "desc", null,
        ImmutableMap.of("foo", "bar"));
    SecretSeries expected = SecretSeries.of(id, "newSecretSeries", "desc", now, "creator", now,
        "creator", null, ImmutableMap.of("foo", "bar"), null);

    assertThat(tableSize()).isEqualTo(before + 1);

    SecretSeries actual = secretSeriesDAO.getSecretSeriesByName("newSecretSeries")
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
        "name", "description", "type", "generationOptions");

    actual = secretSeriesDAO.getSecretSeriesById(id)
        .orElseThrow(RuntimeException::new);
    assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
        "name", "description", "type", "generationOptions");
  }

  @Test public void setCurrentVersion() {
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesByName", "creator", "", null, null);
    Optional<SecretSeries> secretSeriesById = secretSeriesDAO.getSecretSeriesById(id);
    assertThat(secretSeriesById.get().currentVersion().isPresent()).isFalse();

    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah", "checksum", "creator", null, 0);
    secretSeriesDAO.setCurrentVersion(id, contentId);
    secretSeriesById = secretSeriesDAO.getSecretSeriesById(id);
    assertThat(secretSeriesById.get().currentVersion().get()).isEqualTo(contentId);
  }

  @Test(expected = IllegalStateException.class)
  public void setCurrentVersion_failsWithIncorrectSecretContent() {
    long id = secretSeriesDAO.createSecretSeries("someSecret", "creator", "", null, null);
    long other = secretSeriesDAO.createSecretSeries("someOtherSecret", "creator", "", null, null);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(other, "blah", "checksum", "creator", null, 0);

    secretSeriesDAO.setCurrentVersion(id, contentId);
  }

  @Test public void deleteSecretSeriesByName() {
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesByName", "creator", "", null, null);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah", "checksum", "creator", null, 0);
    secretSeriesDAO.setCurrentVersion(id, contentId);
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName").get().currentVersion().isPresent()).isTrue();

    secretSeriesDAO.deleteSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName");
    assertThat(secretSeriesDAO.getSecretSeriesByName("toBeDeleted_deleteSecretSeriesByName").get().currentVersion().isPresent()).isFalse();
  }

  @Test public void deleteSecretSeriesById() {
    long id = secretSeriesDAO.createSecretSeries("toBeDeleted_deleteSecretSeriesById", "creator", "", null, null);
    long contentId = secretContentDAOFactory.readwrite().createSecretContent(id, "blah", "checksum", "creator", null, 0);
    secretSeriesDAO.setCurrentVersion(id, contentId);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).get().currentVersion().isPresent()).isTrue();

    secretSeriesDAO.deleteSecretSeriesById(id);
    assertThat(secretSeriesDAO.getSecretSeriesById(id).get().currentVersion().isPresent()).isFalse();
  }

  @Test public void getNonExistentSecretSeries() {
    assertThat(secretSeriesDAO.getSecretSeriesByName("non-existent")).isEmpty();
    assertThat(secretSeriesDAO.getSecretSeriesById(-2328)).isEmpty();
  }

  private int tableSize() {
    return jooqContext.fetchCount(SECRETS);
  }
}
