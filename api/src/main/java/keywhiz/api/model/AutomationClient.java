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

import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;

/** Special type of {@link Client} with elevated, automation privileges. */
public class AutomationClient extends Client {
  private static final boolean AUTOMATION_ALLOWED_YES = true;

  private AutomationClient(Client client) {
    super(
        client.getId(),
        client.getName(),
        client.getDescription(),
        client.getSpiffeId(),
        client.getCreatedAt(),
        client.getCreatedBy(),
        client.getUpdatedAt(),
        client.getUpdatedBy(),
        client.getLastSeen(),
        client.getExpiration(),
        client.isEnabled(),
        client.getOwner(),
        AUTOMATION_ALLOWED_YES
    );
  }

  @Nullable public static AutomationClient of(Client client) {
    if (client.isAutomationAllowed()) {
      return new AutomationClient(client);
    }

    return null;
  }

  @Override public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", getName())
        .toString();
  }
}
