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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Maps to entity from secrets table. A secret may have many versions, each with different content,
 * but the versions share common information in a SecretSeries, such as name, description, and
 * metadata. In particular, access control is granted to a SecretSeries and not a specific version
 * in that series. New secret versions in the series will "inherit" the same group associations.
 * One-to-many mapping to {@link SecretContent}s.
 */
public class SecretSeries {
  private final long id;
  private final String name;
  private final String description;
  private final OffsetDateTime createdAt;
  private final String createdBy;
  private final OffsetDateTime updatedAt;
  private final String updatedBy;
  private final String type;
  private final ImmutableMap<String, String> generationOptions;

  public SecretSeries(long id,
      String name,
      @Nullable String description,
      OffsetDateTime createdAt,
      String createdBy,
      OffsetDateTime updatedAt,
      String updatedBy,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
    this.updatedAt = updatedAt;
    this.updatedBy = updatedBy;
    this.type = type;
    this.generationOptions = (generationOptions == null) ?
        ImmutableMap.of() : ImmutableMap.copyOf(generationOptions);
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public Optional<String> getType() {
    return Optional.ofNullable(type);
  }

  public ImmutableMap<String, String> getGenerationOptions() {
    return generationOptions;
  }

  @Override public int hashCode() {
    return Objects.hashCode(id, name, description, createdAt, createdBy, updatedAt, updatedBy,
        type, generationOptions);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof SecretSeries) {
      SecretSeries that = (SecretSeries) o;
      if (this.id == that.id &&
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.createdAt, that.createdAt) &&
          Objects.equal(this.createdBy, that.createdBy) &&
          Objects.equal(this.updatedAt, that.updatedAt) &&
          Objects.equal(this.updatedBy, that.updatedBy) &&
          Objects.equal(this.type, that.type) &&
          Objects.equal(this.generationOptions, that.generationOptions)) {
        return true;
      }
    }
    return false;
  }

  @Override public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("name", name)
        .add("description", description)
        .add("createdAt", createdAt)
        .add("createdBy", createdBy)
        .add("updatedAt", updatedAt)
        .add("updatedBy", updatedBy)
        .add("type", type)
        .add("generationOptions", generationOptions)
        .omitNullValues()
        .toString();
  }
}
