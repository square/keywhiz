package keywhiz.service.resources.automation.v2;

import java.util.Collections;
import java.util.UUID;
import javax.inject.Inject;
import keywhiz.KeywhizTestRunner;
import keywhiz.service.daos.SecretDAO;
import keywhiz.service.daos.SecretSeriesDAO;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static keywhiz.jooq.tables.Secrets.SECRETS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(KeywhizTestRunner.class)
public class BackfillRowHmacResourceTest {
  @Inject private BackfillRowHmacResource resource;
  @Inject private SecretDAO.SecretDAOFactory secretDAOFactory;
  @Inject private SecretSeriesDAO.SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Inject private DSLContext dslContext;

  private SecretDAO secretDAO;
  private SecretSeriesDAO secretSeriesDAO;

  @Before
  public void before() {
    secretDAO = secretDAOFactory.readwrite();
    secretSeriesDAO = secretSeriesDAOFactory.readwrite();
  }

  @Test
  public void backfillsNonNullSecretHmac() {
    String secretName = UUID.randomUUID().toString();
    createSecret(secretName);

    String originalHmac = getHmac(secretName);
    assertNotNull(originalHmac);

    String newHmac = UUID.randomUUID().toString();
    secretSeriesDAO.setRowHmacByName(secretName, newHmac);

    resource.backfillSecretRowHmacByName(secretName);

    String updatedHmac = getHmac(secretName);
    assertNotEquals(newHmac, updatedHmac);
    assertEquals(originalHmac, updatedHmac);
  }

  private String getHmac(String secretName) {
    return dslContext.fetchOne(SECRETS, SECRETS.NAME.eq(secretName)).getRowHmac();
  }

  private long createSecret(String name) {
    long secretId = secretDAO.createSecret(
        name,
        null,
        "encryptedSecret",
        "hmac",
        null,
        Collections.emptyMap(),
        0,
        null,
        null,
        null);
    return secretId;
  }
}
