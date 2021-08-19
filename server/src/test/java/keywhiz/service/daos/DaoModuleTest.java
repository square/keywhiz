package keywhiz.service.daos;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javax.inject.Inject;
import keywhiz.service.config.Readonly;
import keywhiz.service.config.Readwrite;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DaoModuleTest {
  @Rule public MethodRule rule = MockitoJUnit.rule();

  @Mock private GroupDAO.GroupDAOFactory groupDAOFactory;
  @Mock private SecretDAO.SecretDAOFactory secretDAOFactory;
  @Mock private SecretSeriesDAO.SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Mock private SecretContentDAO.SecretContentDAOFactory secretContentDAOFactory;

  @Test
  public void injectsClientMapper() {
    assertNotNull(getInstance(ClientMapper.class));
  }

  @Test
  public void injectsReadonlyGroupDAO() {
    GroupDAO dao = mock(GroupDAO.class);
    when(groupDAOFactory.readonly()).thenReturn(dao);

    class Holder {
      @Inject @Readonly GroupDAO dao;
    }

    Holder holder = new Holder();
    inject(holder);
    assertNotNull(holder.dao);
  }

  @Test
  public void injectsReadwriteGroupDAO() {
    GroupDAO dao = mock(GroupDAO.class);
    when(groupDAOFactory.readwrite()).thenReturn(dao);

    class Holder {
      @Inject @Readwrite GroupDAO dao;
    }

    Holder holder = new Holder();
    inject(holder);
    assertNotNull(holder.dao);
  }

  @Test
  public void injectsReadwriteSecretDAO() {
    SecretDAO dao = mock(SecretDAO.class);
    when(secretDAOFactory.readwrite()).thenReturn(dao);

    class Holder {
      @Inject @Readwrite SecretDAO dao;
    }

    Holder holder = new Holder();
    inject(holder);
    assertNotNull(holder.dao);
  }

  @Test
  public void injectsReadwriteSecretSeriesDAO() {
    SecretSeriesDAO dao = mock(SecretSeriesDAO.class);
    when(secretSeriesDAOFactory.readwrite()).thenReturn(dao);

    class Holder {
      @Inject @Readwrite SecretSeriesDAO dao;
    }

    Holder holder = new Holder();
    inject(holder);
    assertNotNull(holder.dao);
  }

  @Test
  public void injectsReadwriteSecretContentDAO() {
    SecretContentDAO dao = mock(SecretContentDAO.class);
    when(secretContentDAOFactory.readwrite()).thenReturn(dao);

    class Holder {
      @Inject @Readwrite SecretContentDAO dao;
    }

    Holder holder = new Holder();
    inject(holder);
    assertNotNull(holder.dao);
  }

  @Test
  public void injectsSecretSeriesMapper() {
    GroupDAO dao = mock(GroupDAO.class);
    when(groupDAOFactory.readonly()).thenReturn(dao);

    assertNotNull(getInstance(SecretSeriesMapper.class));
  }

  private <T> T getInstance(Class<? extends T> clazz) {
    return createInjector().getInstance(clazz);
  }

  private Injector createInjector() {
    return Guice.createInjector(new DaoModule(), new MockModule());
  }

  private void inject(Object o) {
    createInjector().injectMembers(o);
  }

  private class MockModule extends AbstractModule {
    @Override protected void configure() {
      bind(GroupDAO.GroupDAOFactory.class).toInstance(groupDAOFactory);
      bind(SecretDAO.SecretDAOFactory.class).toInstance(secretDAOFactory);
      bind(SecretSeriesDAO.SecretSeriesDAOFactory.class).toInstance(secretSeriesDAOFactory);
      bind(SecretContentDAO.SecretContentDAOFactory.class).toInstance(secretContentDAOFactory);
    }
  }
}
