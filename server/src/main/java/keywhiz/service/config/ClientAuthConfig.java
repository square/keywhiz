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

package keywhiz.service.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.List;

/**
 * Configuration for how clients should be authenticated.
 */
@AutoValue
public abstract class ClientAuthConfig {
  @JsonCreator public static ClientAuthConfig of(
      @JsonProperty("xfcc") List<XfccSourceConfig> sourceConfigs,
      @JsonProperty("type") ClientAuthTypeConfig typeConfig,
      @JsonProperty("createMissingClients") boolean createMissingClients) {
    return new AutoValue_ClientAuthConfig(sourceConfigs, typeConfig, createMissingClients);
  }

  /**
   * connection sources that can set x-forwarded-client-cert headers
   */
  public abstract List<XfccSourceConfig> xfccConfigs();

  /**
   * what identifier(s) to use for clients
   */
  public abstract ClientAuthTypeConfig typeConfig();

  /**
   * whether to create missing (non-automation) clients when a new certificate is presented
   */
  public abstract boolean createMissingClients();
}
