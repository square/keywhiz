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
import java.util.Optional;
import keywhiz.auth.bcrypt.BcryptAuthenticator;
import org.jooq.DSLContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static keywhiz.jooq.tables.Users.USERS;

public class UserDAO {
  private final DSLContext dslContext;

  public UserDAO(DSLContext dslContext) {
    this.dslContext = checkNotNull(dslContext);
  }

  public Optional<String> getHashedPassword(String name) {
    String r = dslContext
        .select(USERS.PASSWORD_HASH)
        .from(USERS)
        .where(USERS.USERNAME.eq(name))
        .fetchOne(USERS.PASSWORD_HASH);
    return Optional.ofNullable(r);
  }

  public void createUserAt(String user, String password, OffsetDateTime created, OffsetDateTime updated) {
    dslContext
      .insertInto(USERS)
      .set(USERS.USERNAME, user)
      .set(USERS.PASSWORD_HASH, BcryptAuthenticator.hashPassword(password))
      .set(USERS.CREATED_AT, created.toEpochSecond())
      .set(USERS.UPDATED_AT, updated.toEpochSecond())
      .execute();
  }

  public void createUser(String user, String password) {
    this.createUserAt(user, password, OffsetDateTime.now(), OffsetDateTime.now());
  }
}
