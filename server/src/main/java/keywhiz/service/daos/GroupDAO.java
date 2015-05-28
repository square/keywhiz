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
import java.util.List;
import java.util.Optional;
import keywhiz.api.model.Group;
import keywhiz.jooq.tables.records.GroupsRecord;
import org.jooq.DSLContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Groups.GROUPS;

public class GroupDAO {

  public long createGroup(DSLContext dslContext, String name, String creator, Optional<String> description) {
    checkNotNull(dslContext);

    GroupsRecord r = dslContext.newRecord(GROUPS);

    r.setName(name);
    r.setCreatedby(creator);
    r.setUpdatedby(creator);
    r.setDescription(description.orElse(null));
    r.store();

    return r.getId();
  }

  public void deleteGroup(DSLContext dslContext, Group group) {
    checkNotNull(dslContext);

    dslContext
        .delete(GROUPS)
        .where(GROUPS.ID.eq(Math.toIntExact(group.getId())))
        .execute();
  }

  public Optional<Group> getGroup(DSLContext dslContext, String name) {
    checkNotNull(dslContext);

    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.NAME.eq(name));
    return Optional.ofNullable(r).map(
        rec -> new GroupMapper().map(rec));
  }

  public Optional<Group> getGroupById(DSLContext dslContext, long id) {
    checkNotNull(dslContext);

    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(
        rec -> new GroupMapper().map(rec));
  }

  public ImmutableSet<Group> getGroups(DSLContext dslContext) {
    checkNotNull(dslContext);

    List<Group> r = dslContext.selectFrom(GROUPS).fetch().map(new GroupMapper());
    return ImmutableSet.copyOf(r);
  }
}
