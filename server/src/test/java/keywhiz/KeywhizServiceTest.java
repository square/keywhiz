package keywhiz;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.google.inject.Injector;
import io.dropwizard.setup.Bootstrap;
import keywhiz.test.ServiceContext;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KeywhizServiceTest {
  @Test
  public void customizedObjectMapperCanSerializeEmptyBeans() throws Exception {
    class EmptyBean {}

    Bootstrap<KeywhizConfig> bootstrap = new Bootstrap<>(new KeywhizService());
    assertThrows(InvalidDefinitionException.class, () ->
        bootstrap.getObjectMapper().writeValueAsString(new EmptyBean()));

    KeywhizService.customizeObjectMapper(bootstrap.getObjectMapper());
    bootstrap.getObjectMapper().writeValueAsString(new EmptyBean());
  }

  @Test
  public void doesNotValidateDatabaseIfDatabaseValidationDisabledInConfig() {
    KeywhizConfig config = mock(KeywhizConfig.class);
    when(config.shouldValidateDatabase()).thenReturn(false);

    ServiceContext context = ServiceContext.create();
    KeywhizService service = context.getService();
    service.validateDatabase(config);

    verify(config, never()).getDataSourceFactory();
  }

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
