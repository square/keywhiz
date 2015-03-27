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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import keywhiz.api.model.Group;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class GroupMapper implements ResultSetMapper<Group> {
  @Override
  public Group map(int index, ResultSet r, StatementContext ctx) throws SQLException {
    return new Group(r.getLong("id"),
                     r.getString("name"),
                     r.getString("description"),
                     r.getTimestamp("createdAt").toLocalDateTime().atOffset(ZoneOffset.UTC),
                     r.getString("createdBy"),
                     r.getTimestamp("updatedAt").toLocalDateTime().atOffset(ZoneOffset.UTC),
                     r.getString("updatedBy"));
  }
}
