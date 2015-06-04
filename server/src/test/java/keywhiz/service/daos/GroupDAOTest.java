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

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import keywhiz.TestDBRule;
import keywhiz.api.model.Group;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.GroupDAO.GroupDAOFactory;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static java.util.stream.Collectors.toList;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static org.assertj.core.api.Assertions.assertThat;

public class GroupDAOTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  @Bind DSLContext jooqContext;
  @Bind @Readonly DSLContext jooqReadonlyContext;

  @Inject GroupDAOFactory groupDAOFactory;

  Group group1, group2;

  GroupDAO groupDAO;

  @Before public void setUp() throws Exception {
    jooqContext = jooqReadonlyContext = testDBRule.jooqContext();
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    groupDAO = groupDAOFactory.readwrite();

    testDBRule.jooqContext().insertInto(GROUPS,
        GROUPS.NAME, GROUPS.DESCRIPTION, GROUPS.CREATEDBY, GROUPS.UPDATEDBY)
        .values("group1", "desc1", "creator1", "updater1")
        .values("group2", "desc2", "creator2", "updater2")
        .execute();

    group1 = groupDAO.getGroup("group1").get();
    group2 = groupDAO.getGroup("group2").get();
  }

  @Test public void createGroup() {
    int before = tableSize();
    groupDAO.createGroup("newGroup", "creator3", Optional.empty());
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
    groupDAO.createGroup("group1", "creator1", Optional.empty());
  }

  private int tableSize() {
    return jooqContext.fetchCount(GROUPS);
  }
}
