package keywhiz.inject;

import com.google.inject.AbstractModule;

public class StrictGuiceModule extends AbstractModule {
  @Override protected void configure() {
    binder().disableCircularProxies();
    binder().requireAtInjectOnConstructors();
    binder().requireExactBindingAnnotations();
  }
}
