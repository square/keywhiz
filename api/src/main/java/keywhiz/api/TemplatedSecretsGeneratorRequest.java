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
import org.hibernate.validator.constraints.NotEmpty;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Request message to create a templated secret.
 */
public class TemplatedSecretsGeneratorRequest {
  @NotEmpty
  private final String name;

  @NotEmpty
  private final String template;

  private final String description;

  private final boolean withVersion;

  @Nullable
  private final ImmutableMap<String, String> metadata;

  public TemplatedSecretsGeneratorRequest(
      @JsonProperty("template") String template,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("withVersion") boolean withVersion,
      @JsonProperty("metadata") @Nullable ImmutableMap<String, String> metadata) {
    this.template = template;
    this.name = name;
    this.description = nullToEmpty(description);
    this.withVersion = withVersion;
    this.metadata = metadata;
  }

  @Override public int hashCode() {
    return Objects.hashCode(name, template, description, withVersion, metadata);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof TemplatedSecretsGeneratorRequest) {
      TemplatedSecretsGeneratorRequest that = (TemplatedSecretsGeneratorRequest) o;
      if (Objects.equal(this.name, that.name) &&
          Objects.equal(this.template, that.template) &&
          Objects.equal(this.description, that.description) &&
          Objects.equal(this.withVersion, that.withVersion) &&
          Objects.equal(this.metadata, that.metadata)) {
        return true;
      }
    }
    return false;
  }

  public String getName() {
    return name;
  }

  public String getTemplate() {
    return template;
  }

  public String getDescription() {
    return description;
  }

  public boolean isWithVersion() {
    return withVersion;
  }

  public ImmutableMap<String, String> getMetadata() {
    return metadata == null ? ImmutableMap.of() : metadata;
  }
}
