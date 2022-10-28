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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import javax.annotation.Nullable;
import org.hibernate.validator.constraints.NotEmpty;

public class CreateGroupRequest {
  private static final String NO_OWNER = null;

  @NotEmpty
  @JsonProperty
  public final String name;

  @Nullable
  @JsonProperty
  public final String description;

  @Nullable
  @JsonProperty
  public final ImmutableMap<String, String> metadata;

  @Nullable
  @JsonProperty
  public final String owner;

  @JsonCreator
  public CreateGroupRequest(@JsonProperty("name") String name,
      @Nullable @JsonProperty("description") String description,
      @Nullable @JsonProperty("metadata") ImmutableMap<String, String> metadata,
      @Nullable @JsonProperty("owner") String owner) {
    this.name = name;
    this.description = description;
    this.metadata = metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata);
    this.owner = owner;
  }

  public CreateGroupRequest(String name,
      String description,
      ImmutableMap<String, String> metadata) {
    this(
        name,
        description,
        metadata,
        NO_OWNER);
  }

      @Override public int hashCode() {
    return Objects.hashCode(
        name,
        description,
        metadata,
        owner);
  }

  @Override public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof CreateGroupRequest) {
      CreateGroupRequest that = (CreateGroupRequest) o;
      return
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.metadata, that.metadata) &&
          Objects.equal(this.owner, that.owner);
    }
    return false;
  }
}
