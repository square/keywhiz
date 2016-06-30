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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.annotation.Nullable;
import keywhiz.api.ApiDate;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Maps to entity from secrets_content table. Contains content and related information on a specific
 * version of a secret. Many-to-one mapping with a {@link SecretSeries}.
 */
@AutoValue
public abstract class SecretContent {
  public static SecretContent of(long id, long secretSeriesId, String encryptedContent, ApiDate createdAt,
                                 @Nullable String createdBy, ApiDate updatedAt, @Nullable String updatedBy,
                                 ImmutableMap<String, String> metadata, long expiry) {
    return new AutoValue_SecretContent(id, secretSeriesId, encryptedContent,
        createdAt, nullToEmpty(createdBy), updatedAt,
        nullToEmpty(updatedBy), metadata, expiry);
  }

  public abstract long id();
  public abstract long secretSeriesId();
  public abstract String encryptedContent();
  public abstract ApiDate createdAt();
  public abstract String createdBy();
  public abstract ApiDate updatedAt();
  public abstract String updatedBy();
  @JsonAnyGetter public abstract  ImmutableMap<String, String> metadata();
  public abstract long expiry();

  @Override public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id())
        .add("secretSeriesId", secretSeriesId())
        .add("encryptedContent", "[REDACTED]")
        .add("createdAt", createdAt())
        .add("createdBy", createdBy())
        .add("updatedAt", updatedAt())
        .add("updatedBy", updatedBy())
        .add("metadata", metadata())
        .add("expiry", expiry())
        .omitNullValues().toString();
  }
}
