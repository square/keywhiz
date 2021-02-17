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
import javax.annotation.Nullable;

/**
 * Configuration for x-forwarded-client-cert header support, as set by the Envoy proxy
 * https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_conn_man/headers#x-forwarded-client-cert
 */
@AutoValue
public abstract class XfccSourceConfig {
  @JsonCreator public static XfccSourceConfig of(
      @JsonProperty("port") Integer port,
      @JsonProperty("allowedClientNames") List<String> allowedClientNames,
      @JsonProperty("allowedSpiffeIds") List<String> allowedSpiffeIds,
      @JsonProperty("callerSpiffeIdHeader") String callerSpiffeIdHeader) {
    return new AutoValue_XfccSourceConfig(port, allowedClientNames, allowedSpiffeIds,
        callerSpiffeIdHeader);
  }

  /**
   * The port that this configuration applies to. All traffic sent through this port
   * must use an XFCC header to identify the Keywhiz client.
   */
  public abstract Integer port();

  /**
   * Only traffic from these clients will be allowed to include an x-forwarded-client-cert header;
   * traffic which includes an XFCC header and is not sent from these clients will be rejected,
   * since most clients should only access their own secrets rather than potentially accessing other
   * secrets using the XFCC header.
   */
  public abstract List<String> allowedClientNames();

  public abstract List<String> allowedSpiffeIds();

  /**
   * An optional custom header identifies the "caller" who sent the original request. If present,
   * we attempt to parse the client Spiffe Id from this header instead of the XFCC header.
   *
   * It is to support a scenario where a real client is behind a proxy. The Spiffe Id extracted from
   * the XFCC header may or may not match the one set in this custom header. For example,
   * considering a request flow like this: client -> proxy -> Envoy -> Keywhiz. In this case, the
   * XFCC header set by the envoy instance contains the proxy cert, while the custom Spiffe Id
   * header contains the client information behind the proxy.
   */
  @Nullable public abstract String callerSpiffeIdHeader();
}
