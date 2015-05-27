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

package keywhiz.cli;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.net.HttpCookie;

/** JSON Serializable HttpCookie object */
@AutoValue
public abstract class JsonCookie {
  @JsonCreator public static JsonCookie create(
      @JsonProperty("name") String name,
      @JsonProperty("value") String value,
      @JsonProperty("domain") String domain,
      @JsonProperty("path") String path,
      @JsonProperty("secure") boolean isSecure,
      @JsonProperty("httpOnly") boolean isHttpOnly) {
    return new AutoValue_JsonCookie(name, value, domain, path, isSecure, isHttpOnly);
  }

  public static JsonCookie fromHttpCookie(HttpCookie cookie) {
    return JsonCookie.create(
        cookie.getName(),
        cookie.getValue(),
        cookie.getDomain(),
        cookie.getPath(),
        cookie.getSecure(),
        cookie.isHttpOnly());
  }

  public static HttpCookie toHttpCookie(JsonCookie cookieContents) {
    HttpCookie cookie = new HttpCookie(cookieContents.name(), cookieContents.value());
    cookie.setDomain(cookieContents.domain());
    cookie.setPath(cookieContents.path());
    cookie.setSecure(cookieContents.isSecure());
    cookie.setHttpOnly(cookieContents.isHttpOnly());
    cookie.setVersion(1); // Always set version to 1 or important fields will be dropped
    return cookie;
  }

  @JsonProperty public abstract String name();
  @JsonProperty public abstract String value();
  @JsonProperty public abstract String domain();
  @JsonProperty public abstract String path();
  @JsonProperty public abstract boolean isSecure();
  @JsonProperty public abstract boolean isHttpOnly();
}
