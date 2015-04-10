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

package keywhiz.service.daos;

import java.time.OffsetDateTime;
import keywhiz.TestDBRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;

import static keywhiz.jooq.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;

public class UserDAOTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();

  UserDAO userDAO;
  String hashedPassword;

  @Before public void setUp() {
    userDAO = new UserDAO(testDBRule.jooqContext());

    hashedPassword = BCrypt.hashpw("password", BCrypt.gensalt());

    testDBRule.jooqContext().delete(USERS).execute();

    testDBRule.jooqContext().insertInto(USERS,
        USERS.USERNAME, USERS.PASSWORD_HASH, USERS.CREATED_AT, USERS.UPDATED_AT)
        .values("user", hashedPassword, OffsetDateTime.now(), OffsetDateTime.now())
        .execute();
  }

  @Test
  public void getExistingUser() {
    String retrievedHash = userDAO.getHashedPassword("user").orElseThrow(RuntimeException::new);
    assertThat(retrievedHash).isEqualTo(hashedPassword);
  }

  @Test
  public void getNonexistentUser() {
    assertThat(userDAO.getHashedPassword("non-user").isPresent()).isFalse();
  }

}