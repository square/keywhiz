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
import javax.annotation.Nullable;
import org.hibernate.validator.constraints.NotBlank;

public class CreateClientRequest {
  private static final String NO_OWNER = null;

  @NotBlank
  @JsonProperty
  public String name;

  @Nullable
  @JsonProperty
  public String owner;

  @JsonCreator
  public CreateClientRequest(
      @JsonProperty("name") String name,
      @JsonProperty("owner") String owner) {
    this.name = name;
    this.owner = owner;
  }

  public CreateClientRequest(String name) {
    this(name, NO_OWNER);
  }

  @Override public int hashCode() {
    return Objects.hashCode(
        name,
        owner);
  }

  @Override public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof CreateClientRequest) {
      CreateClientRequest that = (CreateClientRequest) o;
      return
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.owner, that.owner);
    }
    return false;
  }
}
