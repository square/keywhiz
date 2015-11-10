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
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import static keywhiz.JooqHealthCheck.OnFailure.LOG_ONLY;
import static keywhiz.JooqHealthCheck.OnFailure.RETURN_UNHEALTHY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(KeywhizTestRunner.class)
public class JooqHealthCheckTest {
  @Inject ManagedDataSource dataSource;

  @Mock ManagedDataSource managedDataSource;

  @Test
  public void reportsHealthy() throws Exception {
    JooqHealthCheck healthCheck = new JooqHealthCheck(dataSource, LOG_ONLY);
    assertThat(healthCheck.check()).isEqualTo(HealthCheck.Result.healthy());

    healthCheck = new JooqHealthCheck(dataSource, RETURN_UNHEALTHY);
    assertThat(healthCheck.check()).isEqualTo(HealthCheck.Result.healthy());
  }

  @Test
  public void reportsUnhealthy() throws Exception {
    Connection connection;
    try (Connection c = dataSource.getConnection()) {
      connection = c;
    }
    when(managedDataSource.getConnection()).thenReturn(connection);
    JooqHealthCheck healthCheck = new JooqHealthCheck(managedDataSource, RETURN_UNHEALTHY);
    assertThat(healthCheck.check()).isEqualTo(
        HealthCheck.Result.unhealthy("Unhealthy connection to database."));
  }

  @Test
  public void reportsHealthyWhenLogOnlyIsEnabled() throws Exception {
    Connection connection;
    try (Connection c = dataSource.getConnection()) {
      connection = c;
    }
    when(managedDataSource.getConnection()).thenReturn(connection);
    JooqHealthCheck healthCheck = new JooqHealthCheck(managedDataSource, LOG_ONLY);
    assertThat(healthCheck.check()).isEqualTo(HealthCheck.Result.healthy());
  }
}
