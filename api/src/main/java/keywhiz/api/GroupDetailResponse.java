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
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.SanitizedSecret;

public class GroupDetailResponse {

  @JsonProperty
  private final long id;

  @JsonProperty
  private final String name;

  @JsonProperty
  private final String description;

  @JsonProperty
  private final ApiDate creationDate;

  @JsonProperty
  private final ApiDate updateDate;

  @JsonProperty
  private final String createdBy;

  @JsonProperty
  private final String updatedBy;

  @JsonProperty
  private final ImmutableList<SanitizedSecret> secrets;

  @JsonProperty
  private final ImmutableList<Client> clients;

  public GroupDetailResponse(@JsonProperty("id") long id,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("creationDate") ApiDate creationDate,
      @JsonProperty("updateDate") ApiDate updateDate,
      @JsonProperty("createdBy") String createdBy,
      @JsonProperty("updatedBy") String updatedBy,
      @JsonProperty("secrets") ImmutableList<SanitizedSecret> secrets,
      @JsonProperty("clients") ImmutableList<Client> clients) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.creationDate = creationDate;
    this.updateDate = updateDate;
    this.createdBy = createdBy;
    this.updatedBy = updatedBy;
    this.secrets = secrets;
    this.clients = clients;
  }

  public static GroupDetailResponse fromGroup(Group group, ImmutableList<SanitizedSecret> secrets,
      ImmutableList<Client> clients) {
    return new GroupDetailResponse(group.getId(),
        group.getName(),
        group.getDescription(),
        group.getCreatedAt(),
        group.getUpdatedAt(),
        group.getCreatedBy(),
        group.getUpdatedBy(),
        secrets,
        clients);
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

  public ApiDate getCreationDate() {
    return creationDate;
  }

  public ApiDate getUpdateDate() {
    return updateDate;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  /** @return List of secrets the group has access to. The secrets do not contain content. */
  public ImmutableList<SanitizedSecret> getSecrets() {
    return secrets;
  }

  public ImmutableList<Client> getClients() {
    return clients;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, name, description, creationDate, updateDate, createdBy, updatedBy, secrets, clients);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof GroupDetailResponse) {
      GroupDetailResponse that = (GroupDetailResponse) o;
      if (Objects.equal(this.id, that.id) &&
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.creationDate, that.creationDate) &&
          Objects.equal(this.updateDate, that.updateDate) &&
          Objects.equal(this.createdBy, that.createdBy) &&
          Objects.equal(this.updatedBy, that.updatedBy) &&
          Objects.equal(this.secrets, that.secrets) &&
          Objects.equal(this.clients, that.clients)) {
        return true;
      }
    }
    return false;
  }
}
