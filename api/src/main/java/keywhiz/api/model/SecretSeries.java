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
  @JsonCreator public static SecretSeries of(
      @JsonProperty("id") long id,
      @JsonProperty("name") String name,
      @JsonProperty("owner") String owner,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("createdAt") ApiDate createdAt,
      @JsonProperty("createdBy") @Nullable String createdBy,
      @JsonProperty("updatedAt") ApiDate updatedAt,
      @JsonProperty("updatedBy") @Nullable String updatedBy,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("generationOptions") @Nullable Map<String, String> generationOptions,
      @JsonProperty("currentVersion") @Nullable Long currentVersion) {

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

  @JsonProperty("id") public abstract long id();
  @JsonProperty("name") public abstract String name();
  @JsonProperty("owner") @Nullable public abstract String owner();
  @JsonProperty("description") public abstract String description();
  @JsonProperty("createdAt") public abstract ApiDate createdAt();
  @JsonProperty("createdBy") public abstract String createdBy();
  @JsonProperty("updatedAt") public abstract ApiDate updatedAt();
  @JsonProperty("updatedBy") public abstract String updatedBy();
  @JsonProperty("type") public abstract Optional<String> type();
  @JsonProperty("generationOptions") public abstract ImmutableMap<String, String> generationOptions();
  @JsonProperty("currentVersion") public abstract Optional<Long> currentVersion();

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
