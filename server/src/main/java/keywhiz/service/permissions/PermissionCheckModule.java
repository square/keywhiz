package keywhiz.service.permissions;

import com.google.inject.AbstractModule;

public class PermissionCheckModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(PermissionCheck.class).to(AlwaysFailPermissionCheck.class);
  }
}
