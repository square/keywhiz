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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Maps to entity from secrets table. A secret may have many versions, each with different content,
 * but the versions share common information in a SecretSeries, such as name, description, and
 * metadata. In particular, access control is granted to a SecretSeries and not a specific version
 * in that series. New secret versions in the series will "inherit" the same group associations.
 * One-to-many mapping to {@link SecretContent}s.
 */
@AutoValue
public abstract class SecretSeries {
  public static SecretSeries of(
      long id,
      String name,
      @Nullable String description,
      OffsetDateTime createdAt,
      @Nullable String createdBy,
      OffsetDateTime updatedAt,
      @Nullable String updatedBy,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    ImmutableMap<String, String> options = (generationOptions == null) ?
        ImmutableMap.of() : ImmutableMap.copyOf(generationOptions);
    return new AutoValue_SecretSeries(id, name, nullToEmpty(description), createdAt, nullToEmpty(createdBy), updatedAt, nullToEmpty(updatedBy), Optional.ofNullable(type), options);
  }

  public abstract long id();
  public abstract String name();
  public abstract String description();
  public abstract OffsetDateTime createdAt();
  public abstract String createdBy();
  public abstract OffsetDateTime updatedAt();
  public abstract String updatedBy();
  public abstract Optional<String> type();
  public abstract ImmutableMap<String, String> generationOptions();
}
