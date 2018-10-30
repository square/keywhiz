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

package keywhiz.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.auto.service.AutoService;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.jackson.Discoverable;
import org.jooq.DSLContext;

@AutoService(Discoverable.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface UserAuthenticatorFactory extends Discoverable {
  /**
   * Builds an authenticator from username/password credentials to a {@link User}.
   */
  Authenticator<BasicCredentials, User> build(DSLContext dslContext);
}
