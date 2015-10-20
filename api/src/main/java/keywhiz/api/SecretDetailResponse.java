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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;

public class SecretDetailResponse {
  @JsonProperty
  public final long id;

  @JsonProperty
  public final String name;

  @JsonProperty
  public final String description;

  @JsonProperty
  public final ApiDate createdAt;

  /** User who created the record. */
  @JsonProperty
  public final String createdBy;

  /** Should equal createdAt, but added for consistency in the API. */
  @JsonProperty
  public final ApiDate updatedAt;

  /** User who updated the record. */
  @JsonProperty
  public final String updatedBy;

  @JsonProperty
  public final boolean isVersioned;

  /** Arbitrary key-value data associated with the secret. */
  @JsonProperty
  public final ImmutableMap<String, String> metadata;

  @JsonProperty
  public final ImmutableList<Group> groups;

  @JsonProperty
  public final ImmutableList<Client> clients;

  public SecretDetailResponse(@JsonProperty("id") long id,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("createdAt") ApiDate createdAt,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedAt") ApiDate updatedAt,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("isVersioned") boolean versioned,
      @JsonProperty("metadata") ImmutableMap<String, String> metadata,
      @JsonProperty("groups") ImmutableList<Group> groups,
      @JsonProperty("clients") ImmutableList<Client> clients) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
    this.updatedAt = updatedAt;
    this.updatedBy = updatedBy;
    isVersioned = versioned;
    this.metadata = metadata;
    this.groups = groups;
    this.clients = clients;
  }

  public static SecretDetailResponse fromSecret(Secret secret, ImmutableList<Group> groups,
      ImmutableList<Client> clients) {
    return new SecretDetailResponse(secret.getId(),
        secret.getName(),
        secret.getDescription(),
        secret.getCreatedAt(),
        secret.getCreatedBy(),
        secret.getUpdatedAt(),
        secret.getUpdatedBy(),
        !secret.getVersion().isEmpty(),
        secret.getMetadata(),
        groups,
        clients);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, name, description, createdAt, createdBy, updatedAt, updatedBy,
        metadata, groups, clients);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SecretDetailResponse) {
      SecretDetailResponse that = (SecretDetailResponse) o;
      if (this.id == that.id &&
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.createdAt, that.createdAt) &&
          Objects.equal(this.createdBy, that.createdBy) &&
          Objects.equal(this.updatedAt, that.updatedAt) &&
          Objects.equal(this.updatedBy, that.updatedBy) &&
          this.isVersioned == that.isVersioned &&
          Objects.equal(this.metadata, that.metadata) &&
          Objects.equal(this.groups, that.groups) &&
          Objects.equal(this.clients, that.clients)) {
        return true;
      }
    }
    return false;
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
        .add("isVersioned", isVersioned)
        .add("metadata", metadata)
        .add("groups", "[OMIT]")
        .add("clients", "[OMIT]")
        .toString();
  }
}
