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
import com.google.common.collect.ImmutableMap;
import javax.annotation.Nullable;
import keywhiz.api.validation.ValidBase64;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Request message to create secrets.
 */
public class CreateSecretRequest {
  @NotEmpty
  @JsonProperty
  public final String name;

  @Nullable
  @JsonProperty
  public final String description;

  /** Base64 representation of the secret to be added. */
  @NotEmpty @ValidBase64
  @JsonProperty
  public final String content;

  @JsonProperty
  public final boolean withVersion;

  @Nullable
  @JsonProperty
  public final ImmutableMap<String, String> metadata;

  /** In seconds since epoch */
  @JsonProperty
  public final long expiry;

  public CreateSecretRequest(@JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("content") String content,
      @JsonProperty("withVersion") boolean withVersion,
      @JsonProperty("metadata") @Nullable ImmutableMap<String, String> metadata,
      @JsonProperty("expiry") long expiry) {
    this.name = name;
    this.description = description;
    this.content = content;
    this.withVersion = withVersion;
    this.metadata = metadata;
    this.expiry = expiry;
  }

  @Override public int hashCode() {
    return Objects.hashCode(name, description, content, withVersion, metadata, expiry);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof CreateSecretRequest) {
      CreateSecretRequest that = (CreateSecretRequest) o;
      if (Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.content, that.content) &&
          Objects.equal(this.withVersion, that.withVersion) &&
          Objects.equal(this.metadata, that.metadata) &&
          this.expiry == that.expiry) {
        return true;
      }
    }
    return false;
  }
}
