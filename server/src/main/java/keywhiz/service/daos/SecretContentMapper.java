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
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import keywhiz.api.model.SecretContent;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import org.jooq.RecordMapper;

class SecretContentMapper implements RecordMapper<SecretsContentRecord, SecretContent> {
  private static final TypeReference MAP_STRING_STRING_TYPE =
      new TypeReference<Map<String, String>>() {};
  private final ObjectMapper mapper;

  @Inject SecretContentMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public SecretContent map(SecretsContentRecord r) {
    return SecretContent.of(
        r.getId(),
        r.getSecretid(),
        r.getEncryptedContent(),
        r.getVersion(),
        r.getCreatedat(),
        r.getCreatedby(),
        r.getUpdatedat(),
        r.getUpdatedby(),
        tryToReadMapFromMetadata(r));
  }

  private ImmutableMap<String, String> tryToReadMapFromMetadata(SecretsContentRecord r) {
    String value = r.getMetadata();
    if (!value.isEmpty()) {
      try {
        return ImmutableMap.copyOf(mapper.readValue(value, MAP_STRING_STRING_TYPE));
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to create a Map from data. Bad json in metadata column?", e);
      }
    }
    return ImmutableMap.of();
  }
}
