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

import java.util.Optional;
import javax.inject.Inject;
import org.jooq.DSLContext;

import static keywhiz.jooq.tables.Users.USERS;

/**
 * Jooq version of UserDAO.
 */
public class UserJooqDao {
  private final DSLContext dslContext;

  @Inject
  public UserJooqDao(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  public Optional<String> getHashedPassword(String name) {
    String r = dslContext
        .select(USERS.PASSWORD_HASH)
        .from(USERS)
        .where(USERS.USERNAME.eq(name))
        .fetchOne(USERS.PASSWORD_HASH);
    return Optional.ofNullable(r);
  }
}