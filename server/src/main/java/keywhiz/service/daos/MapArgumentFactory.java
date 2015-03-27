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
import com.google.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

/** Allows mapping Maps as DB values, which are serialized as JSON. */
public class MapArgumentFactory implements ArgumentFactory<Map<?,?>> {
  private final ObjectMapper mapper;

  @Inject
  public MapArgumentFactory(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
    return value instanceof Map;
  }

  @Override public Argument build(Class<?> expectedType, Map<?,?> value, StatementContext ctx) {
    return new MapArgument(mapper, value);
  }

  private static class MapArgument implements Argument {
    private final ObjectMapper mapper;
    private final Map<?,?> value;

    public MapArgument(ObjectMapper mapper, Map<?,?> value) {
      this.mapper = mapper;
      this.value = value;
    }

    @Override public void apply(int position, PreparedStatement statement, StatementContext ctx)
        throws SQLException {
      if (value == null || value.isEmpty()) {
        statement.setObject(position, "");
      } else {
        try {
          statement.setObject(position, mapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
          throw Throwables.propagate(e);
        }
      }
    }
  }
}
