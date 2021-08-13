package keywhiz.test;

import com.google.inject.Injector;
import keywhiz.inject.InjectorFactory;

public final class KeywhizTests {
  private KeywhizTests() {}

  public static Injector createInjector() {
    ServiceContext context = ServiceContext.create();
    Injector injector = InjectorFactory.createInjector(context.getConfig(), context.getEnvironment());
    context.getService().setInjector(injector);
    return injector;
  }
}
