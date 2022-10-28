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
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import javax.annotation.Nullable;
import keywhiz.api.ApiDate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

/**
 * Groups entry for collecting a set of {@link Client}s, via membership, and/or {@link Secret}s, via
 * access grants.
 */
public class Group {
  private static final String NO_OWNER =  null;

  @JsonProperty
  private final long id;

  @JsonProperty
  private final String name;

  @JsonProperty
  private final String description;

  @JsonProperty
  private final ApiDate createdAt;

  @JsonProperty
  private final String createdBy;

  @JsonProperty
  private final ApiDate updatedAt;

  @JsonProperty
  private final String updatedBy;

  /** Key-value metadata of the group. */
  @JsonProperty
  private final ImmutableMap<String, String> metadata;

  @JsonProperty
  private final String owner;

  @JsonCreator
  public Group(@JsonProperty("id") long id,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("createdAt") ApiDate createdAt,
      @JsonProperty("createdBy") @Nullable String createdBy,
      @JsonProperty("updatedAt") ApiDate updatedAt,
      @JsonProperty("updatedBy") @Nullable String updatedBy,
      @JsonProperty("metadata") @Nullable ImmutableMap<String, String> metadata,
      @JsonProperty("owner") @Nullable String owner) {
    this.id = id;
    this.name = checkNotNull(name, "Group name must not be null");
    this.description = nullToEmpty(description);
    this.createdAt = createdAt;
    this.createdBy = nullToEmpty(createdBy);
    this.updatedAt = updatedAt;
    this.updatedBy = nullToEmpty(updatedBy);
    this.metadata = ImmutableMap.copyOf(metadata == null ? ImmutableMap.of() : metadata);
    this.owner = owner;
  }

  public Group(long id,
      String name,
      String description,
      ApiDate createdAt,
      String createdBy,
      ApiDate updatedAt,
      String updatedBy,
      ImmutableMap<String, String> metadata) {
    this(
        id,
        name,
        description,
        createdAt,
        createdBy,
        updatedAt,
        updatedBy,
        metadata,
        NO_OWNER);
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public ApiDate getCreatedAt() { return createdAt; }

  public String getCreatedBy() {
    return createdBy;
  }

  public ApiDate getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public ImmutableMap<String, String> getMetadata() { return metadata; }

  public String getOwner() { return owner; }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Group) {
      Group that = (Group) o;
      if (this.id == that.id &&
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.createdAt, that.createdAt) &&
          Objects.equal(this.createdBy, that.createdBy) &&
          Objects.equal(this.updatedAt, that.updatedAt) &&
          Objects.equal(this.updatedBy, that.updatedBy) &&
          Objects.equal(this.metadata, that.metadata) &&
          Objects.equal(this.owner, that.owner)) {
        return true;
      }
    }
    return false;
  }

  @Override public int hashCode() {
    return Objects.hashCode(
        id,
        name,
        description,
        createdAt,
        createdBy,
        updatedAt,
        updatedBy,
        metadata,
        owner);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("name", name)
        .add("description", description)
        .add("createdAt", createdAt)
        .add("createdBy", createdBy)
        .add("updatedAt", updatedAt)
        .add("updatedBy", updatedBy)
        .add("metadata", metadata)
        .add("owner", owner)
        .toString();
  }
}
