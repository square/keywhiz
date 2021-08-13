package keywhiz;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.db.ManagedDataSource;
import javax.inject.Inject;
import keywhiz.ServiceDataSourceModule;
import keywhiz.inject.ContextModule;
import keywhiz.service.config.Readonly;
import keywhiz.test.ServiceContext;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class ServiceDataSourceModuleTest {
  @Test
  public void injectsDataSources() {
    class Holder {
      @Inject ManagedDataSource readWrite;
      @Inject @Readonly ManagedDataSource readonly;
    }

    Holder holder = new Holder();

    ServiceContext context = ServiceContext.create();

    Guice
        .createInjector(
            new ContextModule(context.getConfig(), context.getEnvironment()),
            new ServiceDataSourceModule())
        .injectMembers(holder);

    assertNotNull(holder.readWrite);
    assertNotNull(holder.readonly);
  }

  @Test
  public void dataSourcesAreSingletons() {
    class Holder {
      @Inject ManagedDataSource readWrite;
      @Inject @Readonly ManagedDataSource readonly;
    }

    Holder holder1 = new Holder();
    Holder holder2 = new Holder();

    ServiceContext context = ServiceContext.create();

    Injector injector = Guice
        .createInjector(
            new ContextModule(context.getConfig(), context.getEnvironment()),
            new ServiceDataSourceModule());

    injector.injectMembers(holder1);
    injector.injectMembers(holder2);

    assertSame(holder1.readWrite, holder2.readWrite);
    assertSame(holder1.readonly, holder2.readonly);
  }
}
