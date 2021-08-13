package keywhiz.service.daos;

import org.junit.Test;

import static keywhiz.test.KeywhizTests.createInjector;
import static org.junit.Assert.assertNotNull;

public class DaoModuleTest {
  @Test
  public void injectsClientMapper() {
    assertNotNull(createInjector().getInstance(ClientMapper.class));
  }
}
