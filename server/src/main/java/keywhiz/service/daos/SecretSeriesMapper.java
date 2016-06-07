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
import javax.inject.Inject;
import keywhiz.api.ApiDate;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.RecordMapper;

class SecretSeriesMapper implements RecordMapper<SecretsRecord, SecretSeries> {
  private static final TypeReference MAP_STRING_STRING_TYPE =
      new TypeReference<Map<String, String>>() {};
  private final ObjectMapper mapper;

  @Inject SecretSeriesMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SecretSeries map(SecretsRecord r) {
    return SecretSeries.of(
        r.getId(),
        r.getName(),
        r.getDescription(),
        new ApiDate(r.getCreatedat()),
        r.getCreatedby(),
        new ApiDate(r.getUpdatedat()),
        r.getUpdatedby(),
        r.getType(),
        tryToReadMapValue(r),
        r.getCurrent());
  }

  private Map<String, String> tryToReadMapValue(SecretsRecord r) {
    String value = r.getOptions();
    if (!value.isEmpty()) {
      try {
        return mapper.readValue(value, MAP_STRING_STRING_TYPE);
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to create a Map from data. Bad json in options column?", e);
      }
    }
    return null;
  }
}
