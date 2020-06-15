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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration for how clients should be authenticated. */
@AutoValue
public abstract class ClientAuthTypeConfig {
  @JsonCreator public static ClientAuthTypeConfig of(
      @JsonProperty("useClientName") boolean useClientName,
      @JsonProperty("useSpiffeId") boolean useSpiffeId) {
    return new AutoValue_ClientAuthTypeConfig(useClientName, useSpiffeId);
  }

  // whether to use the client name to identify clients
  public abstract boolean useClientName();

  // whether to use the spiffe ID to identify clients
  public abstract boolean useSpiffeId();
}
