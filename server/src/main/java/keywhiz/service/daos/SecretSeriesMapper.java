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
import java.io.IOException;
import java.util.Map;
import keywhiz.api.model.SecretSeries;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.RecordMapper;

import static java.lang.String.format;
import static keywhiz.jooq.tables.Secrets.SECRETS;

class SecretSeriesMapper implements RecordMapper<Record, SecretSeries> {
  private static final TypeReference MAP_STRING_STRING_TYPE =
      new TypeReference<Map<String, String>>() {};
  private final ObjectMapper mapper;

  SecretSeriesMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SecretSeries map(Record r) {
    return new SecretSeries(
        r.getValue(SECRETS.ID),
        r.getValue(SECRETS.NAME),
        r.getValue(SECRETS.DESCRIPTION),
        r.getValue(SECRETS.CREATEDAT),
        r.getValue(SECRETS.CREATEDBY),
        r.getValue(SECRETS.UPDATEDAT),
        r.getValue(SECRETS.UPDATEDBY),
        r.getValue(SECRETS.TYPE),
        tryToReadMapValue(r, SECRETS.OPTIONS));
  }

  private Map<String, String> tryToReadMapValue(Record r, Field<String> field) {
    String value = r.getValue(field);
    Map<String, String> map = null;
    if (!value.isEmpty()) {
      try {
        map = mapper.readValue(value, MAP_STRING_STRING_TYPE);
      } catch (IOException e) {
        throw new RuntimeException(
            format("Failed to create a Map from data. Bad json in %s column?", field.getName()), e);
      }
    }
    return map;
  }
}
