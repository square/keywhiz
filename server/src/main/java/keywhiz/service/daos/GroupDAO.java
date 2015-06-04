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

import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import keywhiz.api.model.Group;
import keywhiz.jooq.tables.records.GroupsRecord;
import keywhiz.service.config.Readonly;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Groups.GROUPS;

public class GroupDAO {
  private final DSLContext dslContext;
  private final GroupMapper groupMapper;

  private GroupDAO(DSLContext dslContext, GroupMapper groupMapper) {
    this.dslContext = dslContext;
    this.groupMapper = groupMapper;
  }

  public long createGroup(String name, String creator, Optional<String> description) {
    GroupsRecord r = dslContext.newRecord(GROUPS);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    r.setName(name);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setDescription(description.orElse(null));
    r.store();

    return r.getId();
  }

  public void deleteGroup(Group group) {
    dslContext
        .delete(GROUPS)
        .where(GROUPS.ID.eq(Math.toIntExact(group.getId())))
        .execute();
  }

  public Optional<Group> getGroup(String name) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.NAME.eq(name));
    return Optional.ofNullable(r).map(groupMapper::map);
  }

  public Optional<Group> getGroupById(long id) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(groupMapper::map);
  }

  public ImmutableSet<Group> getGroups() {
    List<Group> r = dslContext.selectFrom(GROUPS).fetch().map(groupMapper);
    return ImmutableSet.copyOf(r);
  }

  public static class GroupDAOFactory implements DAOFactory<GroupDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final GroupMapper groupMapper;

    @Inject public GroupDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        GroupMapper groupMapper) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.groupMapper = groupMapper;
    }

    @Override public GroupDAO readwrite() {
      return new GroupDAO(jooq, groupMapper);
    }

    @Override public GroupDAO readonly() {
      return new GroupDAO(readonlyJooq, groupMapper);
    }

    @Override public GroupDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new GroupDAO(dslContext, groupMapper);
    }
  }
}
