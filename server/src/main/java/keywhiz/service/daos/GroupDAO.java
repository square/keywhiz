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

import java.util.Optional;
import java.util.Set;
import keywhiz.api.model.Group;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.customizers.SingleValueResult;

@RegisterMapper(GroupMapper.class)
public interface GroupDAO {
  @GetGeneratedKeys
  @SqlUpdate("INSERT INTO groups (name, createdBy, updatedBy, description) " +
             "VALUES (:name, :creator, :creator, :desc)")
  public long createGroup(@Bind("name") String name, @Bind("creator") String creator,
      @Bind("desc") Optional<String> description);

  @SqlUpdate("DELETE FROM groups WHERE id = :id")
  public void deleteGroup(@BindBean Group Group);

  @SingleValueResult(Group.class)
  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy " +
            "FROM groups WHERE name = :name")
  public Optional<Group> getGroup(@Bind("name") String name);

  @SingleValueResult(Group.class)
  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy " +
      "FROM groups WHERE id = :id")
  public Optional<Group> getGroupById(@Bind("id") long id);

  // Write update methods as needed.

  @SqlQuery("SELECT id, name, description, createdAt, createdBy, updatedAt, updatedBy FROM groups")
  public Set<Group> getGroups();
}
