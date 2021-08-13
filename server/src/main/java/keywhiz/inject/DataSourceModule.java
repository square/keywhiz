package keywhiz.inject;

import com.google.inject.AbstractModule;
import io.dropwizard.db.ManagedDataSource;
import keywhiz.service.config.Readonly;

class DataSourceModule extends AbstractModule {
  private final ManagedDataSource readWrite;
  private final ManagedDataSource readonly;

  public DataSourceModule(ManagedDataSource readWrite, ManagedDataSource readonly) {
    this.readWrite = readWrite;
    this.readonly = readonly;
  }

  @Override protected void configure() {
    bind(ManagedDataSource.class).toInstance(readWrite);
    bind(ManagedDataSource.class).annotatedWith(Readonly.class).toInstance(readonly);
  }
}
