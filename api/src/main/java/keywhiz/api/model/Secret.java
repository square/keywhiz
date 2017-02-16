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
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import keywhiz.api.ApiDate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;
import static org.apache.commons.lang3.StringUtils.chomp;
import static org.apache.commons.lang3.StringUtils.removeEnd;

/**
 * Immutable Secret data model.
 *
 * Private fields and getters used for immutability with defaults. We don't count on the Constructor
 * with arguments due to de-serialization.
 */
public class Secret {
  /** Row id of secret series. */
  private final long id;

  /** Name of secret or the secret series. */
  private final String name;

  private final String description;

  /** Base64-encoded content of this version of the secret. */
  private String secret;
  private final LazyString encryptedSecret;
  private final String checksum;

  private final ApiDate createdAt;
  private final String createdBy;
  private final ApiDate updatedAt;
  private final String updatedBy;

  /** Key-value metadata of the secret. */
  private final ImmutableMap<String, String> metadata;

  private final String type;
  private final ImmutableMap<String, String> generationOptions;

  private final long expiry;

  /** Current version of the secret (may be null) */
  private final Long version;

  public Secret(long id,
                String name,
                @Nullable String description,
                LazyString encryptedSecret,
                String checksum,
                ApiDate createdAt,
                @Nullable String createdBy,
                ApiDate updatedAt,
                @Nullable String updatedBy,
                @Nullable Map<String, String> metadata,
                @Nullable String type,
                @Nullable Map<String, String> generationOptions,
                long expiry,
                @Nullable Long version) {

    checkArgument(!name.isEmpty());
    this.id = id;
    this.name = name;
    this.description = nullToEmpty(description);
    this.encryptedSecret = checkNotNull(encryptedSecret);
    this.checksum = checksum;
    this.createdAt = checkNotNull(createdAt);
    this.createdBy = nullToEmpty(createdBy);
    this.updatedAt = checkNotNull(updatedAt);
    this.updatedBy = nullToEmpty(updatedBy);
    this.metadata = (metadata == null) ?
        ImmutableMap.of() : ImmutableMap.copyOf(metadata);
    this.type = type;
    this.generationOptions = (generationOptions == null) ?
        ImmutableMap.of() : ImmutableMap.copyOf(generationOptions);
    this.expiry = expiry;
    this.version = version;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /** @return Name to serialize for clients. */
  public String getDisplayName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getSecret() {
    if (secret == null) {
      secret = checkNotNull(encryptedSecret.decrypt());
    }
    return secret;
  }

  public String getChecksum() {
    return checksum;
  }

  public ApiDate getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public ApiDate getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public ImmutableMap<String, String> getMetadata() {
    return metadata;
  }

  public Optional<String> getType() {
    return Optional.ofNullable(type);
  }

  public ImmutableMap<String, String> getGenerationOptions() {
    return generationOptions;
  }

  public long getExpiry() {
    return expiry;
  }

  public Optional<Long> getVersion() {return Optional.ofNullable(version); }

  /** Slightly hokey way of calculating the decoded-length without bothering to decode. */
  public static int decodedLength(String secret) {
    checkNotNull(secret);
    // Remove newlines and padding from the end of our secret string.
    secret = removeEnd(removeEnd(chomp(secret), "="), "=");
    return (secret.length() * 3) / 4;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Secret) {
      Secret that = (Secret) o;
      if (Objects.equal(this.id, that.id) &&
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.getSecret(), that.getSecret()) &&
          Objects.equal(this.getChecksum(), that.getChecksum()) &&
          Objects.equal(this.createdAt, that.createdAt) &&
          Objects.equal(this.createdBy, that.createdBy) &&
          Objects.equal(this.updatedAt, that.updatedAt) &&
          Objects.equal(this.updatedBy, that.updatedBy) &&
          Objects.equal(this.metadata, that.metadata) &&
          Objects.equal(this.type, that.type) &&
          Objects.equal(this.generationOptions, that.generationOptions) &&
          this.expiry == that.expiry &&
          Objects.equal(this.version, that.version)) {
        return true;
      }
    }
    return false;
  }

  @Override public int hashCode() {
    return Objects.hashCode(id, name, description, getSecret(), checksum, createdAt, createdBy, updatedAt,
        updatedBy, metadata, type, generationOptions, expiry);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("name", name)
        .add("description", description)
        .add("secret", "[REDACTED]")
        .add("checksum", checksum)
        .add("creationDate", createdAt)
        .add("createdBy", createdBy)
        .add("updatedDate", updatedAt)
        .add("updatedBy", updatedBy)
        .add("metadata", metadata)
        .add("type", type)
        .add("generationOptions", generationOptions)
        .add("expiry", expiry)
        .add("version", version)
        .toString();
  }

  public interface LazyString {
    String decrypt();
  }
}
