package keywhiz.service.daos;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import keywhiz.service.config.Readonly;
import keywhiz.service.config.Readwrite;

public class DaoModule extends AbstractModule {
  @Override protected void configure() {
    bind(ClientMapper.class).toProvider(ClientMapper::new);
  }

  @Provides
  @Singleton
  @Readonly
  GroupDAO readonlyGroupDAO(GroupDAO.GroupDAOFactory factory) {
    return factory.readonly();
  }

  @Provides
  @Singleton
  @Readwrite
  GroupDAO readwriteGroupDAO(GroupDAO.GroupDAOFactory factory) {
    return factory.readwrite();
  }

  @Provides
  @Singleton
  @Readwrite
  SecretDAO readwriteSecretDAO(SecretDAO.SecretDAOFactory factory) {
    return factory.readwrite();
  }

  @Provides
  @Singleton
  @Readwrite
  SecretSeriesDAO readwriteSecretSeriesDAO(SecretSeriesDAO.SecretSeriesDAOFactory factory) {
    return factory.readwrite();
  }

  @Provides
  @Singleton
  @Readwrite
  SecretContentDAO readwriteSecretContentDAO(SecretContentDAO.SecretContentDAOFactory factory) {
    return factory.readwrite();
  }
}
