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
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import keywhiz.api.ApiDate;

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
      String owner,
      @Nullable String description,
      ApiDate createdAt,
      @Nullable String createdBy,
      ApiDate updatedAt,
      @Nullable String updatedBy,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions,
      @Nullable Long currentVersion) {

    ImmutableMap<String, String> options = (generationOptions == null) ?
        ImmutableMap.of() : ImmutableMap.copyOf(generationOptions);

    return builder()
        .id(id)
        .name(name)
        .owner(owner)
        .description(nullToEmpty(description))
        .createdAt(createdAt)
        .createdBy(nullToEmpty(createdBy))
        .updatedAt(updatedAt)
        .updatedBy(nullToEmpty(updatedBy))
        .type(Optional.ofNullable(type))
        .generationOptions(options)
        .currentVersion(Optional.ofNullable(currentVersion))
        .build();
  }

  public abstract long id();
  public abstract String name();
  @Nullable public abstract String owner();
  public abstract String description();
  public abstract ApiDate createdAt();
  public abstract String createdBy();
  public abstract ApiDate updatedAt();
  public abstract String updatedBy();
  public abstract Optional<String> type();
  public abstract ImmutableMap<String, String> generationOptions();
  public abstract Optional<Long> currentVersion();

  public static Builder builder() {
    return new AutoValue_SecretSeries.Builder();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(long id);
    public abstract Builder name(String name);
    @Nullable public abstract Builder owner(String owner);
    public abstract Builder description(String description);
    public abstract Builder createdAt(ApiDate createdAt);
    public abstract Builder createdBy(String createdBy);
    public abstract Builder updatedAt(ApiDate updatedAt);
    public abstract Builder updatedBy(String updatedBy);
    public abstract Builder type(Optional<String> type);
    public abstract Builder generationOptions(ImmutableMap<String, String> options);
    public abstract Builder currentVersion(Optional<Long> currentVersion);

    public abstract SecretSeries build();
  }
}
