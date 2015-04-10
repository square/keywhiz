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

import java.util.List;
import java.util.Optional;
import keywhiz.TestDBRule;
import keywhiz.api.model.Group;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupJooqDaoTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  Group group1, group2;

  GroupJooqDao groupJooqDao;

  @Before
  public void setUp() throws Exception {
    groupJooqDao = new GroupJooqDao(testDBRule.jooqContext());

    testDBRule.jooqContext().insertInto(GROUPS,
        GROUPS.NAME, GROUPS.DESCRIPTION, GROUPS.CREATEDBY, GROUPS.UPDATEDBY)
        .values("group1", "desc1", "creator1", "updater1")
        .values("group2", "desc2", "creator2", "updater2")
        .execute();

    group1 = groupJooqDao.getGroup("group1").get();
    group2 = groupJooqDao.getGroup("group2").get();
  }

  @Test
  public void createGroup() {
    int before = tableSize();
    groupJooqDao.createGroup("newGroup", "creator3", Optional.empty());
    assertThat(tableSize()).isEqualTo(before + 1);

    List<String> names = groupJooqDao.getGroups()
        .stream()
        .map(Group::getName)
        .collect(toList());
    assertThat(names).contains("newGroup");
  }

  @Test
  public void deleteGroup() {
    int before = tableSize();
    groupJooqDao.deleteGroup(group1);

    assertThat(tableSize()).isEqualTo(before - 1);
    assertThat(groupJooqDao.getGroups()).containsOnly(group2);
  }

  @Test
  public void getGroup() {
    // getGroup is performed in setup()
    assertThat(group1.getName()).isEqualTo("group1");
    assertThat(group1.getDescription()).isEqualTo("desc1");
    assertThat(group1.getCreatedBy()).isEqualTo("creator1");
    assertThat(group1.getUpdatedBy()).isEqualTo("updater1");
  }

  @Test
  public void getGroupById() {
    Group group = groupJooqDao.getGroupById(group1.getId())
        .orElseThrow(RuntimeException::new);
    assertThat(group).isEqualTo(group1);
  }

  @Test
  public void getNonExistentGroup() {
    assertThat(groupJooqDao.getGroup("non-existent").isPresent()).isFalse();
    assertThat(groupJooqDao.getGroupById(-1234).isPresent()).isFalse();
  }

  @Test
  public void getGroups() {
    assertThat(groupJooqDao.getGroups()).containsOnly(group1, group2);
  }

  @Test(expected = DataAccessException.class)
  public void willNotCreateDuplicateGroup() throws Exception {
    groupJooqDao.createGroup("group1", "creator1", Optional.empty());
  }

  private int tableSize() {
    return testDBRule.jooqContext().fetchCount(GROUPS);
  }
}
