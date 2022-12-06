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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import keywhiz.api.model.Group;
import keywhiz.jooq.tables.Groups;
import keywhiz.jooq.tables.records.GroupsRecord;
import keywhiz.service.config.Readonly;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.Tables.CLIENTS;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;
import static keywhiz.jooq.tables.Secrets.SECRETS;

public class GroupDAO {
  private static final Long NO_OWNER = null;

  private static final Groups GROUP_OWNERS = GROUPS.as("owners");

  private final DSLContext dslContext;
  private final GroupMapper groupMapper;
  private final ObjectMapper mapper;

  private GroupDAO(DSLContext dslContext, GroupMapper groupMapper, ObjectMapper mapper) {
    this.dslContext = dslContext;
    this.groupMapper = groupMapper;
    this.mapper = mapper;
  }

  public long createGroup(
      String name,
      String creator,
      String description,
      ImmutableMap<String, String> metadata) {
    return createGroup(
        name,
        creator,
        description,
        metadata,
        NO_OWNER);
  }

  public long createGroup(
      String name,
      String creator,
      String description,
      ImmutableMap<String, String> metadata,
      Long ownerId) {
    GroupsRecord r = dslContext.newRecord(GROUPS);

    String jsonMetadata;
    try {
      jsonMetadata = mapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      // Serialization of a Map<String, String> can never fail.
      throw Throwables.propagate(e);
    }

    long now = OffsetDateTime.now().toEpochSecond();

    r.setName(name);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setDescription(description);
    r.setMetadata(jsonMetadata);
    r.setOwner(ownerId);
    r.store();

    return r.getId();
  }

  public void deleteGroup(Group group) {
    dslContext.transaction(configuration -> {
      DSL.using(configuration)
              .delete(GROUPS)
              .where(GROUPS.ID.eq(group.getId()))
              .execute();
      DSL.using(configuration)
              .delete(MEMBERSHIPS)
              .where(MEMBERSHIPS.GROUPID.eq(group.getId()))
              .execute();
      DSL.using(configuration)
              .delete(ACCESSGRANTS)
              .where(ACCESSGRANTS.GROUPID.eq(group.getId()))
              .execute();
      DSL.using(configuration)
          .update(SECRETS)
          .set(SECRETS.OWNER, (Long) null)
          .where(SECRETS.OWNER.eq(group.getId()))
          .execute();
      DSL.using(configuration)
          .update(GROUPS)
          .set(GROUPS.OWNER, (Long) null)
          .where(GROUPS.OWNER.eq(group.getId()))
          .execute();
      DSL.using(configuration)
          .update(CLIENTS)
          .set(CLIENTS.OWNER, (Long) null)
          .where(CLIENTS.OWNER.eq(group.getId()))
          .execute();
    });
  }

  public Optional<Group> getGroup(String name) {
    return getGroup(GROUPS.NAME.eq(name));
  }

  public Optional<Group> getGroupById(long id) {
    return getGroup(GROUPS.ID.eq(id));
  }

  private Optional<Group> getGroup(Condition condition) {
    Record record = dslContext
        .select(GROUPS.fields())
        .select(GROUP_OWNERS.ID, GROUP_OWNERS.NAME)
        .from(GROUPS)
        .leftJoin(GROUP_OWNERS)
        .on(GROUPS.OWNER.eq(GROUP_OWNERS.ID))
        .where(condition)
        .fetchOne();

    return Optional.ofNullable(recordToGroup(record));
  }

  private Group recordToGroup(Record record) {
    if (record == null) {
      return null;
    }

    GroupsRecord groupRecord = record.into(GROUPS);
    GroupsRecord ownerRecord = record.into(GROUP_OWNERS);

    boolean danglingOwner = groupRecord.getOwner() != null && ownerRecord.getId() == null;
    if (danglingOwner) {
      throw new IllegalStateException(
          String.format(
              "Owner %s for group %s is missing.",
              groupRecord.getOwner(),
              groupRecord.getName()));
    }

    Group group = groupMapper.map(groupRecord);
    if (ownerRecord != null) {
      group.setOwner(ownerRecord.getName());
    }

    return group;
  }

  public ImmutableSet<Group> getGroups() {
    Result<Record> results = dslContext
        .select(GROUPS.fields())
        .select(GROUP_OWNERS.NAME)
        .from(GROUPS)
        .leftJoin(GROUP_OWNERS)
        .on(GROUPS.OWNER.eq(GROUP_OWNERS.ID))
        .fetch();

    Set<Group> groups = results.stream()
        .map(this::recordToGroup)
        .collect(Collectors.toSet());

    return ImmutableSet.copyOf(groups);
  }

  public static class GroupDAOFactory implements DAOFactory<GroupDAO> {
    private final DSLContext jooq;
    private final DSLContext readonlyJooq;
    private final GroupMapper groupMapper;
    private final ObjectMapper mapper;

    @Inject public GroupDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        GroupMapper groupMapper, ObjectMapper mapper) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.groupMapper = groupMapper;
      this.mapper = mapper;
    }

    @Override public GroupDAO readwrite() {
      return new GroupDAO(jooq, groupMapper, mapper);
    }

    @Override public GroupDAO readonly() {
      return new GroupDAO(readonlyJooq, groupMapper, mapper);
    }

    @Override public GroupDAO using(Configuration configuration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      return new GroupDAO(dslContext, groupMapper, mapper);
    }

    public GroupDAO using(DSLContext dslContext) {
      return new GroupDAO(dslContext, groupMapper, mapper);
    }
  }
}
