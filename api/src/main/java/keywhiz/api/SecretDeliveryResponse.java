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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.Map;
import keywhiz.api.model.SanitizedSecret;
import keywhiz.api.model.Secret;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.api.model.Secret.decodedLength;

/** JSON Serialization class for a REST response {@link Secret}. API View. */
public class SecretDeliveryResponse {
  private final String name;
  private final String secret;
  private final int secretLength;
  private final ApiDate creationDate;
  private final boolean isVersioned;
  private final ImmutableMap<String, String> metadata;

  public SecretDeliveryResponse(
      @JsonProperty("name") String name,
      @JsonProperty("secret") String secret,
      @JsonProperty("secretLength") int secretLength,
      @JsonProperty("creationDate") ApiDate creationDate,
      @JsonProperty("isVersioned") boolean isVersioned,
      @JsonProperty("metadata") ImmutableMap<String, String> metadata) {
    this.name = name;
    this.secret = secret;
    this.secretLength = secretLength;
    this.creationDate = creationDate;
    this.isVersioned = isVersioned;
    this.metadata = metadata;
  }

  public static SecretDeliveryResponse fromSecret(Secret secret) {
    checkNotNull(secret);
    return new SecretDeliveryResponse(secret.getDisplayName(),
        secret.getSecret(),
        decodedLength(secret.getSecret()),
        secret.getCreatedAt(),
        !secret.getVersion().isEmpty(),
        secret.getMetadata());
  }

  public static SecretDeliveryResponse fromSanitizedSecret(SanitizedSecret sanitizedSecret) {
    checkNotNull(sanitizedSecret);
    return new SecretDeliveryResponse(SanitizedSecret.displayName(sanitizedSecret),
        "",
        0,
        sanitizedSecret.createdAt(),
        !sanitizedSecret.version().isEmpty(),
        sanitizedSecret.metadata());
  }

  /** @return External name of the secret. */
  public String getName() {
    return name;
  }

  /** @return Base64-encoded secret content. */
  public String getSecret() {
    return secret;
  }

  /** @return In bytes, the decoded length of the secret. */
  public int getSecretLength() {
    return secretLength;
  }

  /** @return ISO-8601 datetime the secret was created. */
  public ApiDate getCreationDate() {
    return creationDate;
  }

  @JsonProperty("isVersioned")
  public boolean isVersioned() {
    return isVersioned;
  }

  /** @return Arbitrary key-values, serialized with existing fields. */
  @JsonAnyGetter
  public Map<String, String> getMetadata() {
    return metadata;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getName(), getSecret(), getSecretLength(), getCreationDate(),
        isVersioned(), metadata);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SecretDeliveryResponse) {
      SecretDeliveryResponse that = (SecretDeliveryResponse) o;
      if (Objects.equal(this.getName(), that.getName()) &&
          Objects.equal(this.getSecret(), that.getSecret()) &&
          this.getSecretLength() == that.getSecretLength() &&
          Objects.equal(this.getCreationDate(), that.getCreationDate()) &&
          this.isVersioned() == that.isVersioned() &&
          Objects.equal(this.metadata, that.metadata)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", getName())
        .add("secret", "[REDACTED]")
        .add("secretLength", getSecretLength())
        .add("creationDate", getCreationDate())
        .add("isVersioned", isVersioned())
        .add("metadata", metadata)
        .toString();
  }
}
