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

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.java8.auth.Authenticator;
import java.util.Optional;
import keywhiz.auth.User;
import keywhiz.service.daos.UserDAO;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class BcryptAuthenticator implements Authenticator<BasicCredentials, User> {
  private static final Logger logger = LoggerFactory.getLogger(BcryptAuthenticator.class);
  private final UserDAO userDAO;

  public BcryptAuthenticator(UserDAO userDAO) {
    this.userDAO = checkNotNull(userDAO);
  }

  @Override public Optional<User> authenticate(BasicCredentials credentials)
      throws AuthenticationException {
    User user = null;
    String username = credentials.getUsername();
    if (!User.isSanitizedUsername(username)) {
      logger.info("Username: {} must match pattern: {}", username, User.USERNAME_PATTERN);
      return Optional.empty();
    }

    String password = credentials.getPassword();

    // Get hashed password column from BCrypt table by username
    Optional<String> optionalHashedPwForUser = userDAO.getHashedPassword(username);
    if (!optionalHashedPwForUser.isPresent()) {
      return Optional.empty();
    }

    if (BCrypt.checkpw(password, optionalHashedPwForUser.get())) {
      user = User.named(username);
    }

    return Optional.ofNullable(user);
  }
}
