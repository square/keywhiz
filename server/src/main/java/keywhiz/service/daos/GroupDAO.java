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
import keywhiz.service.config.Readonly;
import keywhiz.service.config.ShadowWrite;
import keywhiz.shadow_write.jooq.tables.Groups;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Accessgrants.ACCESSGRANTS;
import static keywhiz.jooq.tables.Groups.GROUPS;
import static keywhiz.jooq.tables.Memberships.MEMBERSHIPS;

public class GroupDAO {
  private static final Logger logger = LoggerFactory.getLogger(GroupDAO.class);

  private final DSLContext dslContext;
  private final GroupMapper groupMapper;
  private final DSLContext shadowWriteDslContext;

  private GroupDAO(DSLContext dslContext, GroupMapper groupMapper, DSLContext shadowWriteDslContext) {
    this.dslContext = dslContext;
    this.groupMapper = groupMapper;
    this.shadowWriteDslContext = shadowWriteDslContext;
  }

  public long createGroup(String name, String creator, String description) {
    GroupsRecord r = dslContext.newRecord(GROUPS);

    long now = OffsetDateTime.now().toEpochSecond();

    r.setName(name);
    r.setCreatedby(creator);
    r.setCreatedat(now);
    r.setUpdatedby(creator);
    r.setUpdatedat(now);
    r.setDescription(description);
    r.store();

    long id = r.getId();

    try {
      keywhiz.shadow_write.jooq.tables.records.GroupsRecord shadowR = shadowWriteDslContext.newRecord(Groups.GROUPS);
      shadowR.setId(id);
      shadowR.setName(name);
      shadowR.setCreatedby(creator);
      shadowR.setCreatedat(now);
      shadowR.setUpdatedby(creator);
      shadowR.setUpdatedat(now);
      shadowR.setDescription(description);
      shadowR.store();
    } catch (DataAccessException e) {
      logger.error("shadowWrite: failure to create group groupId {}", e, id);
    }

    return id;
  }

  public void deleteGroup(Group group) {
    long id = group.getId();
    shadowWriteDslContext.transaction(shadowWriteConfiguration -> {
      dslContext.transaction(configuration -> {
        DSL.using(configuration)
            .delete(GROUPS)
            .where(GROUPS.ID.eq(id))
            .execute();
        DSL.using(configuration)
            .delete(MEMBERSHIPS)
            .where(MEMBERSHIPS.GROUPID.eq(id))
            .execute();
        DSL.using(configuration)
            .delete(ACCESSGRANTS)
            .where(ACCESSGRANTS.GROUPID.eq(id))
            .execute();
      });

      try {
        DSL.using(shadowWriteConfiguration)
            .delete(GROUPS)
            .where(GROUPS.ID.eq(id))
            .execute();
        DSL.using(shadowWriteConfiguration)
            .delete(MEMBERSHIPS)
            .where(MEMBERSHIPS.GROUPID.eq(id))
            .execute();
        DSL.using(shadowWriteConfiguration)
            .delete(ACCESSGRANTS)
            .where(ACCESSGRANTS.GROUPID.eq(id))
            .execute();
      } catch (DataAccessException e) {
        logger.error("shadowWrite: failure to delete group groupId {}", e, id);
      }
    });
  }

  public Optional<Group> getGroup(String name) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.NAME.eq(name));
    return Optional.ofNullable(r).map(groupMapper::map);
  }

  public Optional<Group> getGroupById(long id) {
    GroupsRecord r = dslContext.fetchOne(GROUPS, GROUPS.ID.eq(id));
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
    private final DSLContext shadowWriteJooq;

    @Inject public GroupDAOFactory(DSLContext jooq, @Readonly DSLContext readonlyJooq,
        GroupMapper groupMapper, @ShadowWrite DSLContext shadowWriteJooq) {
      this.jooq = jooq;
      this.readonlyJooq = readonlyJooq;
      this.groupMapper = groupMapper;
      this.shadowWriteJooq = shadowWriteJooq;
    }

    @Override public GroupDAO readwrite() {
      return new GroupDAO(jooq, groupMapper, shadowWriteJooq);
    }

    @Override public GroupDAO readonly() {
      return new GroupDAO(readonlyJooq, groupMapper, null);
    }

    @Override public GroupDAO using(Configuration configuration,
        Configuration shadowWriteConfiguration) {
      DSLContext dslContext = DSL.using(checkNotNull(configuration));
      DSLContext shadowWriteDslContext = null;
      if (shadowWriteConfiguration != null) {
        shadowWriteDslContext = DSL.using(checkNotNull(shadowWriteConfiguration));
      }
      return new GroupDAO(dslContext, groupMapper, shadowWriteDslContext);
    }
  }
}
