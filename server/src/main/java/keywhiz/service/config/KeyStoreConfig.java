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
import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration for accessing a keystore. */
@AutoValue
public abstract class KeyStoreConfig {
  private static final Logger logger = LoggerFactory.getLogger(KeyStoreConfig.class);

  @JsonCreator public static KeyStoreConfig of(
      @JsonProperty("path") @NotEmpty String path,
      @JsonProperty("password") @NotEmpty String password,
      @JsonProperty("type") @NotEmpty String type,
      @JsonProperty("alias") @NotEmpty String alias) {
    return new AutoValue_KeyStoreConfig(path, password, type, alias);
  }

  abstract String path();
  abstract String password();
  public abstract String type();
  public abstract String alias();

  /**
   * If the path does not exist on the filesystem, it is resolved as a resource.
   *
   * @return InputStream to the keystore at the resolved path
   * @throws IOException if the path cannot be opened
   */
  public InputStream openPath() throws IOException {
    Path filePath = Paths.get(path());
    if (Files.exists(filePath)) {
      logger.info("Opening keystore at file path {}", filePath);
      return Files.newInputStream(filePath);
    }
    URL resourceUrl = Resources.getResource(path());
    logger.info("Opening keystore at resource path {}", resourceUrl);
    return resourceUrl.openStream();
  }

  /**
   * If the password is templated (e.g. external file), it is resolved before returning.
   *
   * @return resolved password to the keystore
   */
  public String resolvedPassword() {
    try {
      return Templates.evaluateTemplate(password());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
