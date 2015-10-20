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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This is a wrapper for the date class used in API responses so we can have a custom JSON serializer and deserializer
 */
@JsonSerialize(using=ApiDate.ApiDateSerializer.class)
@JsonDeserialize(using=ApiDate.ApiDateDeserializer.class)
public class ApiDate {

  static class ApiDateSerializer extends JsonSerializer<ApiDate> {
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    @Override
    public void serialize(ApiDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(formatter.format(value.offsetDateTime));
    }
  }

  static class ApiDateDeserializer extends JsonDeserializer<ApiDate> {
    @Override
    public ApiDate deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
      return new ApiDate(parser.readValueAs(OffsetDateTime.class));
    }
  }

  public static ApiDate parse(String s) {
    return new ApiDate(OffsetDateTime.parse(s));
  }

  public static ApiDate now() {
    return new ApiDate(OffsetDateTime.now());
  }

  public long toEpochSecond() {
    return this.offsetDateTime.toEpochSecond();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ApiDate) {
      ApiDate that = (ApiDate) obj;
      return this.offsetDateTime.equals(that.offsetDateTime);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return this.offsetDateTime.hashCode();
  }

  public ApiDate(OffsetDateTime odt) {
    this.offsetDateTime = odt;
  }

  public OffsetDateTime offsetDateTime;
}

