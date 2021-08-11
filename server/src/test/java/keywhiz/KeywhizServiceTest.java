package keywhiz;

import com.google.inject.Injector;
import keywhiz.test.ServiceContext;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class KeywhizServiceTest {
  @Test
  public void ensuresInjectorIsCreated() {
    ServiceContext context = ServiceContext.create();
    KeywhizService service = context.getService();
    assertNull(service.getInjector());
    service.ensureInjectorCreated(context.getConfig(), context.getEnvironment());
    assertNotNull(service.getInjector());
  }

  @Test
  public void doesNotRecreateInjector() {
    ServiceContext context = ServiceContext.create();
    KeywhizService service = context.getService();

    service.ensureInjectorCreated(context.getConfig(), context.getEnvironment());
    Injector first = service.getInjector();

    service.ensureInjectorCreated(context.getConfig(), context.getEnvironment());
    Injector second = service.getInjector();

    assertSame(first, second);
  }
}
