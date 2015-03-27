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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Map;
import keywhiz.KeywhizService;
import keywhiz.api.model.SecretSeries;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import static java.lang.String.format;

public class SecretSeriesMapper implements ResultSetMapper<SecretSeries> {
  private static final TypeReference MAP_STRING_STRING_TYPE =
      new TypeReference<Map<String, String>>() {};
  private final ObjectMapper mapper = KeywhizService.customizeObjectMapper(Jackson.newObjectMapper());

  @Override
  public SecretSeries map(int index, ResultSet r, StatementContext ctx) throws SQLException {
    return new SecretSeries(r.getLong("id"),
                            r.getString("name"),
                            r.getString("description"),
                            r.getTimestamp("createdAt").toLocalDateTime().atOffset(ZoneOffset.UTC),
                            r.getString("createdBy"),
                            r.getTimestamp("updatedAt").toLocalDateTime().atOffset(ZoneOffset.UTC),
                            r.getString("updatedBy"),
                            tryToReadMapValue(r, "metadata"),
                            r.getString("type"),
                            tryToReadMapValue(r, "options"));
  }

  private Map<String, String> tryToReadMapValue(ResultSet resultSet, String fieldName)
      throws SQLException {
    Map<String, String> map = null;
    String field = resultSet.getString(fieldName);
    if (!field.isEmpty()) {
      try {
        map = mapper.readValue(field, MAP_STRING_STRING_TYPE);
      } catch (IOException e) {
        throw new RuntimeException(
            format("Failed to create a Map from data. Bad json in %s column?", fieldName), e);
      }
    }
    return map;
  }
}
