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
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;
import static java.util.regex.Pattern.quote;

/**
 * Immutable Secret data model.
 *
 * Private fields and getters used for immutability with defaults. We don't count on the Constructor
 * with arguments due to de-serialization.
 */
public class Secret {
  public static final String VERSION_DELIMITER = "..";

  /** Row id of secret series. */
  private final long id;

  /** Name of secret or the secret series. */
  private final String name;

  /** Version of secret. May be empty. Lexicographically increasing. */
  private final String version;

  private final String description;

  /** Base64-encoded content of this version of the secret. */
  private final String secret;

  private final OffsetDateTime createdAt;
  private final String createdBy;
  private final OffsetDateTime updatedAt;
  private final String updatedBy;

  /** Key-value metadata of the secret. */
  private final ImmutableMap<String, String> metadata;

  private final String type;
  private final ImmutableMap<String, String> generationOptions;

  public Secret(long id,
      String name,
      @Nullable String version,
      @Nullable String description,
      String secret,
      OffsetDateTime createdAt,
      @Nullable String createdBy,
      OffsetDateTime updatedAt,
      @Nullable String updatedBy,
      @Nullable Map<String, String> metadata,
      @Nullable String type,
      @Nullable Map<String, String> generationOptions) {

    checkArgument(!name.isEmpty());
    this.id = id;
    this.name = name;
    this.version = nullToEmpty(version);
    this.description = nullToEmpty(description);
    this.secret = checkNotNull(secret); /* Expected empty when sanitized. */
    this.createdAt = checkNotNull(createdAt);
    this.createdBy = nullToEmpty(createdBy);
    this.updatedAt = checkNotNull(updatedAt);
    this.updatedBy = nullToEmpty(updatedBy);
    this.metadata = (metadata == null) ?
        ImmutableMap.of() : ImmutableMap.copyOf(metadata);
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

  /** @return Name to serialize for clients. */
  public String getDisplayName() {
    if (version.isEmpty()) {
      return name;
    }

    return name + VERSION_DELIMITER + version;
  }

  public String getVersion() {
    return version;
  }

  public String getDescription() {
    return description;
  }

  public String getSecret() {
    return secret;
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

  public ImmutableMap<String, String> getMetadata() {
    return metadata;
  }

  public Optional<String> getType() {
    return Optional.ofNullable(type);
  }

  public ImmutableMap<String, String> getGenerationOptions() {
    return generationOptions;
  }

  /**
   * @param name original secret name, optionally containing a version.
   * @return an array of size 2, containing name, and (possibly empty) version Strings.
   * @throws ParseException when string contains more than one delimiter.
   */
  public static String[] splitNameAndVersion(String name) throws ParseException {
    if (!name.contains(Secret.VERSION_DELIMITER)) {
      return new String[] {name, ""};
    }

    String[] parts = name.split(quote(Secret.VERSION_DELIMITER));
    if (parts.length > 2) {
      throw new ParseException("Split ambiguous, more then one delimiter.", parts[0].length() + parts[1].length());
    }
    if (parts.length == 1) {
      parts = new String[] {parts[0], ""};
    }
    return parts;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Secret) {
      Secret that = (Secret) o;
      if (Objects.equal(this.id, that.id) &&
          Objects.equal(this.name, that.name) &&
          Objects.equal(this.version, that.version) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.secret, that.secret) &&
          Objects.equal(this.createdAt, that.createdAt) &&
          Objects.equal(this.createdBy, that.createdBy) &&
          Objects.equal(this.updatedAt, that.updatedAt) &&
          Objects.equal(this.updatedBy, that.updatedBy) &&
          Objects.equal(this.metadata, that.metadata) &&
          Objects.equal(this.type, that.type) &&
          Objects.equal(this.generationOptions, that.generationOptions)) {
        return true;
      }
    }
    return false;
  }

  @Override public int hashCode() {
    return Objects.hashCode(id, name, version, description, secret, createdAt, createdBy, updatedAt,
        updatedBy, metadata, type, generationOptions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("id", id)
        .add("name", name)
        .add("version", version)
        .add("description", description)
        .add("secret", "[REDACTED]")
        .add("creationDate", createdAt)
        .add("createdBy", createdBy)
        .add("updatedDate", updatedAt)
        .add("updatedBy", updatedBy)
        .add("metadata", metadata)
        .add("type", type)
        .add("generationOptions", generationOptions)
        .toString();
  }
}
