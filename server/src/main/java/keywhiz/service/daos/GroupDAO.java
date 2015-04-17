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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import keywhiz.api.model.Group;
import keywhiz.jooq.tables.records.GroupsRecord;
import org.jooq.DSLContext;

import static keywhiz.jooq.tables.Groups.GROUPS;

public class GroupDAO {
  private final DSLContext dslContext;

  @Inject public GroupDAO(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  public long createGroup(String name, String creator, Optional<String> description) {
    GroupsRecord r = dslContext.newRecord(GROUPS);

    r.setName(name);
    r.setCreatedby(creator);
    r.setUpdatedby(creator);
    r.setDescription(description.orElse(null));
    r.store();

    return r.getId();
  }

  public void deleteGroup(Group group) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.ID.eq((int) group.getId()));
    r.delete();
  }

  public Optional<Group> getGroup(String name) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.NAME.eq(name));
    if (r != null) {
      return Optional.of(r.map(new GroupMapper()));
    }
    return Optional.empty();
  }

  public Optional<Group> getGroupById(long id) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.ID.eq((int) id));
    if (r != null) {
      return Optional.of(r.map(new GroupMapper()));
    }
    return Optional.empty();
  }

  public Set<Group> getGroups() {
    List<Group> r = dslContext.select().from(GROUPS).fetch().map(new GroupMapper());
    return new HashSet<>(r);
  }
}


