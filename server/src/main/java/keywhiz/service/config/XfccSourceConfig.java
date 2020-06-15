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
 * Configuration for x-forwarded-client-cert header support, as set by the Envoy proxy
 * https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers#x-forwarded-client-cert
 */
@AutoValue
public abstract class XfccSourceConfig {
  @JsonCreator public static XfccSourceConfig of(
      @JsonProperty("allowedPorts") List<Integer> allowedPorts) {
    return new AutoValue_XfccSourceConfig(allowedPorts);
  }

  // Only traffic from these ports will be allowed to include an x-forwarded-client-cert header;
  // traffic which includes an XFCC header and is _not_ sent to these ports will be rejected,
  // since it is likely that the provided certificate belongs to a proxy rather than the original
  // requestor.
  // Access to these ports MUST be highly restricted, since otherwise clients can set the XFCC
  // header to steal other clients' secrets.
  public abstract List<Integer> allowedPorts();
}
