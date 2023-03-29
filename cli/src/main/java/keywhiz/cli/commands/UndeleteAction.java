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

package keywhiz.cli.commands;

import java.io.IOException;
import keywhiz.cli.configs.UndeleteActionConfig;
import keywhiz.client.KeywhizClient;
import okhttp3.Response;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndeleteAction implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(UndeleteAction.class);

  private final UndeleteActionConfig undeleteActionConfig;
  private final KeywhizClient keywhizClient;

  public UndeleteAction(UndeleteActionConfig undeleteActionConfig, KeywhizClient client) {
    this.undeleteActionConfig = undeleteActionConfig;
    this.keywhizClient = client;
  }

  @Override public void run() {
    String type = undeleteActionConfig.objectType;
    Long id = undeleteActionConfig.id;

    if (type == null || type.isEmpty()) {
      throw new IllegalArgumentException("Must specify a type to delete.");
    }

    String trimmedType = type.toLowerCase().trim();
    switch (trimmedType) {
      case "secret":
        undeleteSecret(id);
        break;
      default:
        throw new IllegalArgumentException("Invalid undelete type specified: " + trimmedType);
    }
  }

  private void undeleteSecret(Long id) {
    logger.info("Undeleting secret '{}'.", id);
    try {
      Response response = keywhizClient.undeleteSecret(id);
      switch (response.code()) {
        case HttpStatus.SC_OK:
          logger.info("Successfully undeleted secret.", id);
          break;
        default:
          logger.error(response.message());
          logger.error(response.body().string());
          throw new AssertionError(response.message());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
