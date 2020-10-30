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

package keywhiz.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;

/**
 * This is a wrapper for the date class used in API responses so we can have a custom JSON serializer and deserializer
 */
@JsonSerialize(using=ApiDate.ApiDateSerializer.class)
@JsonDeserialize(using=ApiDate.ApiDateDeserializer.class)
public class ApiDate {
  private long epochSecond;

  public long toEpochSecond() {
    return epochSecond;
  }

  public Instant toInstant() {
    return Instant.ofEpochSecond(epochSecond);
  }

  public ApiDate(long epochSecond) {
    this.epochSecond = epochSecond;
  }

  static class ApiDateSerializer extends JsonSerializer<ApiDate> {
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    @Override
    public void serialize(ApiDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      Instant i = Instant.ofEpochSecond(value.epochSecond);
      gen.writeString(formatter.format(i.atOffset(ZoneOffset.UTC)));
    }
  }

  static class ApiDateDeserializer extends JsonDeserializer<ApiDate> {
    @Override
    public ApiDate deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      OffsetDateTime odt = OffsetDateTime.parse(parser.getText());
      return new ApiDate(odt.toEpochSecond());
    }
  }

  public static ApiDate parse(String s) {
    OffsetDateTime odt = OffsetDateTime.parse(s);
    return new ApiDate(odt.toEpochSecond());
  }

  public static ApiDate now() {
    return new ApiDate(OffsetDateTime.now().toEpochSecond());
  }

  @Override
  public String toString() {
    return format("ApiDate[%d]", epochSecond);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ApiDate) {
      ApiDate that = (ApiDate) obj;
      return this.epochSecond == that.epochSecond;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(this.epochSecond);
  }
}

