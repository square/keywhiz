package keywhiz;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.setup.Environment;
import keywhiz.JooqHealthCheck;
import keywhiz.KeywhizConfig;
import keywhiz.service.config.Readonly;

import static keywhiz.JooqHealthCheck.OnFailure.LOG_ONLY;
import static keywhiz.JooqHealthCheck.OnFailure.RETURN_UNHEALTHY;

public class ServiceDataSourceModule extends AbstractModule {
  @Provides @Singleton ManagedDataSource dataSource(Environment environment,
      KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "db-writable");
    environment.lifecycle().manage(dataSource);

    environment.healthChecks().register("db-read-write-health",
        new JooqHealthCheck(dataSource, LOG_ONLY));

    return dataSource;
  }

  @Provides @Singleton @Readonly ManagedDataSource readonlyDataSource(Environment environment,
      KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getReadonlyDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "db-readonly");
    environment.lifecycle().manage(dataSource);

    environment.healthChecks().register("db-readonly-health",
        new JooqHealthCheck(dataSource, RETURN_UNHEALTHY));

    return dataSource;
  }
}
