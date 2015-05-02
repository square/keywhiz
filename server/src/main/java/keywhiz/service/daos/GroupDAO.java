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
import java.util.List;
import java.util.Optional;
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

    OffsetDateTime now = OffsetDateTime.now();

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
    return Optional.ofNullable(r).map(
        rec -> new GroupMapper().map(rec));
  }

  public Optional<Group> getGroupById(long id) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.ID.eq(Math.toIntExact(id)));
    return Optional.ofNullable(r).map(
        rec -> new GroupMapper().map(rec));
  }

  public ImmutableSet<Group> getGroups() {
    List<Group> r = dslContext.selectFrom(GROUPS).fetch().map(new GroupMapper());
    return ImmutableSet.copyOf(r);
  }
}
