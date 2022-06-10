package keywhiz.service.permissions;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

public class HelloWorldModule extends AbstractModule {
  @Override
  protected void configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(HelloWorld.class), new HelloWorldAnnotationImplementation());
  }
}
