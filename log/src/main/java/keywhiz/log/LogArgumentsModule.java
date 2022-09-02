package keywhiz.log;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

public class LogArgumentsModule extends AbstractModule {
  @Override
  protected void configure() {
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(LogArguments.class), new LogArgumentsMethodInterceptor());
  }
}
