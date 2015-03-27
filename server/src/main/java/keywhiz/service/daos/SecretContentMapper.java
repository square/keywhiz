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
import keywhiz.api.model.SecretContent;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import static java.time.ZoneOffset.UTC;

/** Maps DB query result rows to {@link SecretContent} objects. */
public class SecretContentMapper implements ResultSetMapper<SecretContent> {
  @Override
  public SecretContent map(int index, ResultSet r, StatementContext ctx) throws SQLException {
    return SecretContent.of(r.getLong("id"),
                            r.getLong("secretId"),
                            r.getString("encrypted_content"),
                            r.getString("version"),
                            r.getTimestamp("createdAt").toLocalDateTime().atOffset(UTC),
                            r.getString("createdBy"),
                            r.getTimestamp("updatedAt").toLocalDateTime().atOffset(UTC),
                            r.getString("updatedBy"));
  }
}
