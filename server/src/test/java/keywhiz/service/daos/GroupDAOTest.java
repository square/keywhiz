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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.api.model.Group;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(KeywhizTestRunner.class)
public class GroupDAOTest {
  @Inject DSLContext jooqContext;
  @Inject GroupDAOFactory groupDAOFactory;
  @Inject SecretDAO.SecretDAOFactory secretDAOFactory;

  private Group group1, group2;

  private GroupDAO groupDAO;
  private SecretDAO secretDAO;

  @Before public void setUp() throws Exception {
    groupDAO = groupDAOFactory.readwrite();
    secretDAO = secretDAOFactory.readwrite();

    long now = OffsetDateTime.now().toEpochSecond();

    jooqContext.insertInto(GROUPS, GROUPS.NAME, GROUPS.DESCRIPTION, GROUPS.CREATEDBY,
        GROUPS.UPDATEDBY, GROUPS.CREATEDAT, GROUPS.UPDATEDAT, GROUPS.METADATA)
        .values("group1", "desc1", "creator1", "updater1", now, now, "{\"app\": \"app1\"}")
        .values("group2", "desc2", "creator2", "updater2", now, now, "{\"app\": \"app2\"}")
        .execute();

    group1 = groupDAO.getGroup("group1").get();
    group2 = groupDAO.getGroup("group2").get();
  }

  @Test public void createGroup() {
    int before = tableSize();
    groupDAO.createGroup("newGroup", "creator3", "", ImmutableMap.of());
    assertThat(tableSize()).isEqualTo(before + 1);

    List<String> names = groupDAO.getGroups()
        .stream()
        .map(Group::getName)
        .collect(toList());
    assertThat(names).contains("newGroup");
  }

  @Test public void deleteGroup() {
    int before = tableSize();
    groupDAO.deleteGroup(group1);

    assertThat(tableSize()).isEqualTo(before - 1);
    assertThat(groupDAO.getGroups()).containsOnly(group2);
  }

  @Test public void deleteSetsSecretOwnerToNull() {
    String groupName = randomName();
    long groupId = groupDAO.createGroup(groupName, "creator", "description", ImmutableMap.of());

    long secretId = secretDAO.createSecret(randomName(), groupName, "encryptedSecret", "hmac", "creator",
        Collections.emptyMap(), 0, "description", null, null);

    SecretSeriesAndContent original = secretDAO.getSecretById(secretId).get();
    assertEquals(groupName, original.series().owner());

    groupDAO.deleteGroup(groupDAO.getGroupById(groupId).get());

    SecretSeriesAndContent updated = secretDAO.getSecretById(secretId).get();
    assertNull(updated.series().owner());
  }

  @Test public void getGroup() {
    // getGroup is performed in setup()
    assertThat(group1.getName()).isEqualTo("group1");
    assertThat(group1.getDescription()).isEqualTo("desc1");
    assertThat(group1.getCreatedBy()).isEqualTo("creator1");
    assertThat(group1.getUpdatedBy()).isEqualTo("updater1");
  }

  @Test public void getGroupById() {
    assertThat(groupDAO.getGroupById(group1.getId())).contains(group1);
  }

  @Test public void getNonExistentGroup() {
    assertThat(groupDAO.getGroup("non-existent")).isEmpty();
    assertThat(groupDAO.getGroupById(-1234)).isEmpty();
  }

  @Test public void getGroups() {
    assertThat(groupDAO.getGroups()).containsOnly(group1, group2);
  }

  @Test(expected = DataAccessException.class)
  public void willNotCreateDuplicateGroup() throws Exception {
    groupDAO.createGroup("group1", "creator1", "", ImmutableMap.of());
  }

  private int tableSize() {
    return jooqContext.fetchCount(GROUPS);
  }

  private static String randomName() {
    return UUID.randomUUID().toString();
  }
}
