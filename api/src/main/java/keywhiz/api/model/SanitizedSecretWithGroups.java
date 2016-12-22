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
import java.util.List;
import java.util.stream.Collectors;
import keywhiz.api.ApiDate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link Secret} object, but without the secret content and with group metadata.
 */
@AutoValue
public abstract class SanitizedSecretWithGroups {
  @JsonCreator public static SanitizedSecretWithGroups of(
      @JsonProperty("secret") SanitizedSecret secret,
      @JsonProperty("groups") List<String> groups) {
    return new AutoValue_SanitizedSecretWithGroups(secret, groups);
  }

  public static SanitizedSecretWithGroups of(long id, String name, List<String> groups) {
    SanitizedSecret sanitizedSecret = SanitizedSecret.of(id, name, "", null,
        new ApiDate(0), null, new ApiDate(0), null, null, null, null, 0);
    return SanitizedSecretWithGroups.of(sanitizedSecret, groups);
  }

  public static SanitizedSecretWithGroups fromSecretSeriesAndContentAndGroups(SecretSeriesAndContent seriesAndContent, List<Group> groups) {
    SecretSeries series = seriesAndContent.series();
    SecretContent content = seriesAndContent.content();
    SanitizedSecret sanitizedSecret = SanitizedSecret.of(
        series.id(),
        series.name(),
        content.hmac(),
        series.description(),
        series.createdAt(),
        series.createdBy(),
        series.updatedAt(),
        series.updatedBy(),
        content.metadata(),
        series.type().orElse(null),
        series.generationOptions(),
        content.expiry());
    List<String> stringGroups = groups.stream().map(Group::getName).collect(Collectors.toList());
    return SanitizedSecretWithGroups.of(sanitizedSecret, stringGroups);
  }

  /**
   * Build a matching representation of a secret, but without sensitive content.
   *
   * @param secret secret model to build from
   * @param groups the list of groups
   * @return content of secret model, but without sensitive content
   */
  public static SanitizedSecretWithGroups fromSecret(Secret secret, List<Group> groups) {
    checkNotNull(secret);
    SanitizedSecret sanitizedSecret = SanitizedSecret.of(
        secret.getId(),
        secret.getName(),
        secret.getChecksum(),
        secret.getDescription(),
        secret.getCreatedAt(),
        secret.getCreatedBy(),
        secret.getUpdatedAt(),
        secret.getUpdatedBy(),
        secret.getMetadata(),
        secret.getType().orElse(null),
        secret.getGenerationOptions(),
        secret.getExpiry());
    List<String> stringGroups = groups.stream().map(Group::getName).collect(Collectors.toList());
    return SanitizedSecretWithGroups.of(sanitizedSecret, stringGroups);
  }

  @JsonProperty public abstract SanitizedSecret secret();
  @JsonProperty public abstract List<String> groups();

  /** @return Name to serialize for clients. */
  public static String displayName(SanitizedSecretWithGroups sanitizedSecretWithGroups) {
    String name = sanitizedSecretWithGroups.secret().name();
    return name;
  }
}
