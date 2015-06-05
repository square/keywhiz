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

package keywhiz;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.db.ManagedDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: we could improve the code to use the read-write connection if only the readonly connection
 * is down.
 */
public class JooqHealthCheck extends HealthCheck {
  private final ManagedDataSource dataSource;
  private final OnFailure onFailure;

  private static final Logger logger = LoggerFactory.getLogger(JooqHealthCheck.class);

  enum OnFailure {
    RETURN_UNHEALTHY, LOG_ONLY
  }

  /**
   * The constructor takes a ManagedDataSource instead of a DSLContext so that we can catch and
   * handle any SQLException thrown by DSL.using.
   *
   * @param dataSource connection to monitor
   * @param onFailure allows us to log but report a connection as healthy. Useful for allowing
   * operations in degraded mode.
   */
  public JooqHealthCheck(ManagedDataSource dataSource, OnFailure onFailure) {
    this.dataSource = dataSource;
    this.onFailure = onFailure;
  }

  @Override
  protected Result check() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      DSL.using(connection).selectOne().execute();
    } catch (DataAccessException | SQLException e) {
      switch (onFailure) {
        case LOG_ONLY:
          logger.warn("Unhealthy connection to database.");
          break;
        case RETURN_UNHEALTHY:
          return Result.unhealthy("Unhealthy connection to database.");
      }
    }
    return Result.healthy();
  }
}
