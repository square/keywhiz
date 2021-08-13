package keywhiz.service.crypto;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.security.SecureRandom;
import keywhiz.KeywhizConfig;
import keywhiz.inject.StrictGuiceModule;
import keywhiz.test.ServiceContext;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

public class CryptoModuleTest {
  @Test
  public void secureRandomIsExplicitlyBound() {
    KeywhizConfig config = ServiceContext.create().getConfig();

    Injector injector = Guice.createInjector(
        new CryptoModule(
            config.getDerivationProviderClass(),
            config.getContentKeyStore()),
        new StrictGuiceModule());

    assertNotNull(injector.getInstance(SecureRandom.class));
  }

  @Test
  public void secureRandomIsNonSingleton() {
    KeywhizConfig config = ServiceContext.create().getConfig();

    Injector injector = Guice.createInjector(
        new CryptoModule(
            config.getDerivationProviderClass(),
            config.getContentKeyStore()));

    assertNotSame(injector.getInstance(SecureRandom.class), injector.getInstance(SecureRandom.class));
  }
}
