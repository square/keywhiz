package keywhiz;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.db.ManagedDataSource;
import java.sql.Connection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRule;

import static keywhiz.JooqHealthCheck.OnFailure.LOG_ONLY;
import static keywhiz.JooqHealthCheck.OnFailure.RETURN_UNHEALTHY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class JooqHealthCheckTest {
  @Rule public final TestDBRule testDBRule = new TestDBRule();
  @Rule public TestRule mockito = new MockitoJUnitRule(this);

  @Mock ManagedDataSource managedDataSource;

  @Test
  public void reportsHealthy() throws Exception {
    try (Connection connection = testDBRule.dataSource().getConnection()) {
      when(managedDataSource.getConnection()).thenReturn(connection);
      JooqHealthCheck healthCheck = new JooqHealthCheck(managedDataSource, LOG_ONLY);
      assertThat(healthCheck.check()).isEqualTo(HealthCheck.Result.healthy());
    }

    try (Connection connection = testDBRule.dataSource().getConnection()) {
      when(managedDataSource.getConnection()).thenReturn(connection);
      JooqHealthCheck healthCheck = new JooqHealthCheck(managedDataSource,
          RETURN_UNHEALTHY);
      assertThat(healthCheck.check()).isEqualTo(HealthCheck.Result.healthy());
    }
  }

  @Test
  public void reportsUnhealthy() throws Exception {
    Connection connection;
    try (Connection c = testDBRule.dataSource().getConnection()) {
      connection = c;
    }
    when(managedDataSource.getConnection()).thenReturn(connection);
    JooqHealthCheck healthCheck = new JooqHealthCheck(managedDataSource,
        RETURN_UNHEALTHY);
    assertThat(healthCheck.check()).isEqualTo(
        HealthCheck.Result.unhealthy("Unhealthy connection to database."));
  }

  @Test
  public void reportsUnhealthyWhenLogOnlyIsEnabled() throws Exception {
    Connection connection;
    try (Connection c = testDBRule.dataSource().getConnection()) {
      connection = c;
    }
    when(managedDataSource.getConnection()).thenReturn(connection);
    JooqHealthCheck healthCheck = new JooqHealthCheck(managedDataSource, LOG_ONLY);
    assertThat(healthCheck.check()).isEqualTo(HealthCheck.Result.healthy());
  }
}