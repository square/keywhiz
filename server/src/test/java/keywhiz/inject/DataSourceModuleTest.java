package keywhiz.inject;

import com.google.inject.Guice;
import io.dropwizard.db.ManagedDataSource;
import javax.inject.Inject;
import keywhiz.service.config.Readonly;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class DataSourceModuleTest {
  @Test
  public void injectsDataSources() {
    class Holder {
      @Inject ManagedDataSource readWrite;
      @Inject @Readonly ManagedDataSource readonly;
    }

    Holder holder = new Holder();

    ManagedDataSource readWrite = mock(ManagedDataSource.class);
    ManagedDataSource readonly = mock(ManagedDataSource.class);

    Guice.createInjector(new DataSourceModule(readWrite, readonly)).injectMembers(holder);

    assertSame(readWrite, holder.readWrite);
    assertSame(readonly, holder.readonly);
  }
}
