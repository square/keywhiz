package keywhiz.inject;

import com.google.inject.Injector;
import io.dropwizard.db.ManagedDataSource;
import javax.inject.Inject;
import keywhiz.service.config.Readonly;
import keywhiz.service.daos.UserDAO;
import org.junit.Test;

import static keywhiz.test.KeywhizTests.createInjector;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class InjectorFactoryTest {
  @Test
  public void createsInjector() {
    assertNotNull(createInjector());
  }

  @Test
  public void injectsReadWriteDataSource() {
    class Holder {
      @Inject ManagedDataSource dataSource;
    }
    Holder holder = new Holder();
    createInjector().injectMembers(holder);
    assertNotNull(holder.dataSource);
  }

  @Test
  public void injectsReadonlyDataSource() {
    class Holder {
      @Inject @Readonly ManagedDataSource dataSource;
    }
    Holder holder = new Holder();
    createInjector().injectMembers(holder);
    assertNotNull(holder.dataSource);
  }

  @Test
  public void injectsUserDAO() {
    assertNotNull(createInjector().getInstance(UserDAO.class));
  }
}
