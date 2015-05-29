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

package keywhiz.auth.bcrypt;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.auto.service.AutoService;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.java8.auth.Authenticator;
import keywhiz.auth.User;
import keywhiz.auth.UserAuthenticatorFactory;
import keywhiz.service.daos.UserDAO;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration parameters for using a BCrypt authenticator. */
@AutoService(UserAuthenticatorFactory.class)
@JsonTypeName("bcrypt")
@SuppressWarnings("unused")
public class BcryptAuthenticatorFactory implements UserAuthenticatorFactory {
  private static final Logger logger = LoggerFactory.getLogger(BcryptAuthenticatorFactory.class);

  @Override public Authenticator<BasicCredentials, User> build(DSLContext dslContext) {
    logger.debug("Creating BCrypt authenticator");
    UserDAO userDAO = new UserDAO(dslContext);
    return new BcryptAuthenticator(userDAO);
  }
}
