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
 *
 */

package keywhiz.service.daos;

import javax.annotation.Nullable;
import org.jooq.Configuration;

/**
 * DAO factory implement this interface to provide instances using different underlying database
 * connections.
 *
 * @param <T> DAO type produced by the factory
 */
public interface DAOFactory<T> {
  /**
   * Returns DAO using a read/write database connection.
   */
  T readwrite();

  /**
   * Returns DAO using a read-only database connection.
   */
  T readonly();

  /**
   * Returns DAO using a supplied jOOQ configuration. Useful for issuing queries on the same
   * underlying transaction from a different DAO.
   *
   * For the shadowWrites, we try to handle transactions (to keep things symmetric) but there is
   * no guarantee that the same data will be written to both databases!
   */
  T using(Configuration configuration,
      @Nullable Configuration shadowWriteConfiguration);
}
