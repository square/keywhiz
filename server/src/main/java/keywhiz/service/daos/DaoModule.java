package keywhiz.service.daos;

import com.google.inject.AbstractModule;

public class DaoModule extends AbstractModule {
  @Override protected void configure() {
    bind(ClientMapper.class).toProvider(ClientMapper::new);
  }
}
