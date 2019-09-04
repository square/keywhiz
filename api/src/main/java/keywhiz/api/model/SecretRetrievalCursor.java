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

package keywhiz.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import io.dropwizard.jackson.Jackson;
import java.net.URLDecoder;
import java.net.URLEncoder;
import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * a cursor for use when retrieving secrets with pagination
 */
@AutoValue
public abstract class SecretRetrievalCursor {
  @JsonCreator public static SecretRetrievalCursor of(
      @JsonProperty("name") String name,
      @JsonProperty("expiry") long expiry) {
    return new AutoValue_SecretRetrievalCursor(name, expiry);
  }

  @Nullable public static SecretRetrievalCursor fromUrlEncodedString(@Nullable String encodedJson) {
    if (encodedJson == null) {
      return null;
    }
    String jsonDecoded = URLDecoder.decode(encodedJson, UTF_8);
    ObjectMapper mapper = Jackson.newObjectMapper();
    try {
      return mapper.readValue(jsonDecoded, SecretRetrievalCursor.class);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable public static String toUrlEncodedString(@Nullable SecretRetrievalCursor cursor)
      throws JsonProcessingException {
    if (cursor == null) {
      return null;
    }
    ObjectMapper mapper = Jackson.newObjectMapper();
    // URL-encode the JSON value
    return URLEncoder.encode(mapper.writeValueAsString(cursor), UTF_8);
  }

  @Nullable public static String toString(@Nullable SecretRetrievalCursor cursor)
      throws JsonProcessingException {
    if (cursor == null) {
      return null;
    }
    ObjectMapper mapper = Jackson.newObjectMapper();
    return mapper.writeValueAsString(cursor);
  }

  @JsonProperty public abstract String name();

  @JsonProperty public abstract Long expiry();
}
