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
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import keywhiz.api.ApiDate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.nullToEmpty;

/**
 * {@link Secret} object, but without the secret content.
 */
@AutoValue
public abstract class SanitizedSecret {
  @JsonCreator public static SanitizedSecret of(
      @JsonProperty("id") long id,
      @JsonProperty("name") String name,
      @JsonProperty("version") String version,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("createdAt") ApiDate createdAt,
      @JsonProperty("createdBy") @Nullable String createdBy,
      @JsonProperty("updatedAt") ApiDate updatedAt,
      @JsonProperty("updatedBy") @Nullable String updatedBy,
      @JsonProperty("metadata") @Nullable Map<String, String> metadata,
      @JsonProperty("type") @Nullable String type,
      @JsonProperty("generationOptions") @Nullable Map<String, String> generationOptions) {
    ImmutableMap<String, String> meta =
        (metadata == null) ? ImmutableMap.of() : ImmutableMap.copyOf(metadata);
    ImmutableMap<String, String> genOptions =
        (generationOptions == null) ? ImmutableMap.of() : ImmutableMap.copyOf(generationOptions);
    return new AutoValue_SanitizedSecret(id, name, version, nullToEmpty(description), createdAt,
        nullToEmpty(createdBy), updatedAt, nullToEmpty(updatedBy), meta, Optional.ofNullable(type),
        genOptions);
  }

  public static SanitizedSecret fromSecretSeriesAndContent(SecretSeriesAndContent seriesAndContent) {
    SecretSeries series = seriesAndContent.series();
    SecretContent content = seriesAndContent.content();
    return SanitizedSecret.of(
        series.id(),
        series.name(),
        content.version().orElse(""),
        series.description(),
        content.createdAt(),
        content.createdBy(),
        content.updatedAt(),
        content.updatedBy(),
        content.metadata(),
        series.type().orElse(null),
        series.generationOptions());
  }

  /**
   * Build a matching representation of a secret, but without sensitive content.
   *
   * @param secret secret model to build from
   * @return content of secret model, but without sensitive content
   */
  public static SanitizedSecret fromSecret(Secret secret) {
    checkNotNull(secret);
    return SanitizedSecret.of(
        secret.getId(),
        secret.getName(),
        secret.getVersion(),
        secret.getDescription(),
        secret.getCreatedAt(),
        secret.getCreatedBy(),
        secret.getUpdatedAt(),
        secret.getUpdatedBy(),
        secret.getMetadata(),
        secret.getType().orElse(null),
        secret.getGenerationOptions());
  }

  /**
   * Helper method to convert a collection of secrets to a list of sanitized secrets.
   */
  public static List<SanitizedSecret> fromSecrets(List<Secret> secrets) {
    checkNotNull(secrets);
    ImmutableList.Builder<SanitizedSecret> sanitized = ImmutableList.builder();
    for (Secret secret : secrets) {
      sanitized.add(fromSecret(secret));
    }
    return sanitized.build();
  }

  @JsonProperty public abstract long id();
  @JsonProperty public abstract String name();
  @JsonProperty public abstract String version();
  @JsonProperty public abstract String description();
  @JsonProperty public abstract ApiDate createdAt();
  @JsonProperty public abstract String createdBy();
  @JsonProperty public abstract ApiDate updatedAt();
  @JsonProperty public abstract String updatedBy();
  @JsonProperty public abstract ImmutableMap<String, String> metadata();
  @JsonProperty public abstract Optional<String> type();
  @JsonProperty public abstract ImmutableMap<String, String> generationOptions();

  /** @return Name to serialize for clients. */
  public static String displayName(SanitizedSecret sanitizedSecret) {
    String name = sanitizedSecret.name();
    String version = sanitizedSecret.version();
    if (version.isEmpty()) {
      return name;
    }

    return name + Secret.VERSION_DELIMITER + version;
  }
}
