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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.api.model.Secret.decodedLength;

/**
 * JSON Serialization class for a REST response {@link Secret}.
 * <p>
 * Automation View, does not include secret contents.
 */
@AutoValue
public abstract class AutomationSecretResponse {

  @JsonCreator
  public static AutomationSecretResponse create(
      @JsonProperty("id") long id,
      @JsonProperty("name") String name,
      @JsonProperty("owner") String owner,
      @JsonProperty("secret") String secret,
      @JsonProperty("creationDate") ApiDate creationDate,
      @JsonProperty("metadata") @Nullable ImmutableMap<String, String> metadata,
      @JsonProperty("groups") ImmutableList<Group> groups,
      @JsonProperty("expiry") long expiry) {
    return new AutoValue_AutomationSecretResponse(
        id,
        name,
        owner,
        secret,
        decodedLength(secret),
        creationDate,
        groups,
        metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata),
        expiry);
  }

  public static AutomationSecretResponse fromSecret(Secret secret, ImmutableList<Group> groups) {
    checkNotNull(secret);
    return AutomationSecretResponse.create(
        secret.getId(),
        secret.getDisplayName(),
        secret.getOwner(),
        secret.getSecret(),
        secret.getCreatedAt(),
        secret.getMetadata(),
        groups,
        secret.getExpiry());
  }

  @JsonProperty("id") public abstract long id();

  @JsonProperty("name") public abstract String name();

  @JsonProperty("owner") @Nullable public abstract String owner();

  @JsonProperty("secret") public abstract String secret();

  @JsonProperty("secretLength") public abstract long secretLength();

  @JsonProperty("creationDate") public abstract ApiDate creationDate();

  @JsonProperty("groups") public abstract ImmutableList<Group> groups();

  @JsonAnyGetter @JsonProperty("metadata") public abstract Map<String, String> metadata();

  @JsonProperty("expiry") public abstract long expiry();
}
