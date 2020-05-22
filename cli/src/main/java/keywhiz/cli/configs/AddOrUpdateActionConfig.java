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

package keywhiz.cli.configs;

import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.joda.time.DateTime;

import static com.google.common.base.Strings.nullToEmpty;
import static java.lang.String.format;

public class AddOrUpdateActionConfig {
  @Parameter(names = "--name", description = "Name of the item to add", required = true)
  public String name;

  @Parameter(names = "--description", description = "Description of the item to add or update")
  public String description;

  @Parameter(names = "--spiffeId", description = "Spiffe URI associated with a client (clients only)")
  public String spiffeId;

  @Parameter(names = "--json", description = "Metadata JSON blob")
  public String json;

  @Parameter(names = { "-g", "--group" }, description = "Also assign the secret to this group (secrets only)")
  public String group;

  @Parameter(names = { "-e", "--expiry" }, description = "Secret expiry. For keystores, it is recommended to use the expiry of the earliest key. Format should be 2006-01-02T15:04:05Z or seconds since epoch.")
  public String expiry;

  public String getDescription() {
    return nullToEmpty(description);
  }

  public String getSpiffeId() {
    return nullToEmpty(spiffeId);
  }

  public ImmutableMap<String, String> getMetadata(ObjectMapper mapper) {
    ImmutableMap<String, String> metadata = ImmutableMap.of();
    if (json != null && !json.isEmpty()) {
      TypeReference typeRef = new TypeReference<ImmutableMap<String, String>>() {};
      try {
        metadata = mapper.readValue(json, typeRef);
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
      validateMetadata(metadata);
    }
    return metadata;
  }

  public long getExpiry() {
    if (expiry != null) {
      try {
        return Long.parseLong(expiry);
      } catch (NumberFormatException e) {
      }
      DateTime dt = new DateTime(expiry);
      return dt.getMillis()/1000;
    }
    return 0;
  }

  private static void validateMetadata(ImmutableMap<String, String> metadata) {
    for (ImmutableMap.Entry<String, String> entry : metadata.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      // We want to perform strong validation of the metadata to make sure it is well formed.
      if (!key.matches("(owner|group|mode|filename)")) {
        if(!key.startsWith("_")) {
          throw new IllegalArgumentException(
              format("Illegal metadata key %s: custom metadata keys must start with an underscore", key));
        }
        if(!key.matches("^[a-zA-Z_0-9\\-.:]+$")) {
          throw new IllegalArgumentException(
              format("Illegal metadata key %s: metadata keys can only contain: a-z A-Z 0-9 _ - . :", key));
        }
      }

      if (key.equals("mode") && !value.matches("0[0-7]+")) {
        throw new IllegalArgumentException(format("mode %s is not proper octal", value));
      }
    }
  }

}
