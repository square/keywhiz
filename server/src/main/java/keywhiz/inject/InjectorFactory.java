package keywhiz.inject;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.setup.Environment;
import keywhiz.KeywhizConfig;
import keywhiz.ServiceDataSourceModule;
import keywhiz.ServiceModule;

public final class InjectorFactory {
  private InjectorFactory() {}

  public static Injector createInjector(KeywhizConfig config, Environment environment) {
    return Guice.createInjector(
        new ServiceDataSourceModule(),
        new ServiceModule(config, environment));
  }

  public static Injector createInjector(KeywhizConfig config, Environment environment, ManagedDataSource dataSource) {
    return Guice.createInjector(
        new DataSourceModule(dataSource, dataSource),
        new ServiceModule(config, environment));
  }
}
