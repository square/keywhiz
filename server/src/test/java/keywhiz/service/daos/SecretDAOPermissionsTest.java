package keywhiz.service.daos;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import keywhiz.api.model.Client;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.PermissionCheck;
import org.jooq.DSLContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class SecretDAOPermissionsTest
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DSLContext dslContext;
  @Mock private SecretSeriesDAO.SecretSeriesDAOFactory secretSeriesDAOFactory;
  @Mock private PermissionCheck permissionCheck;
  @Mock private SecretSeriesDAO secretSeriesDAO;
  private SecretDAO secretDAO;

  private static final ImmutableMap<String, String> NO_METADATA = ImmutableMap.of();
  private static final Client
      client = new Client(0, "client", null, null, null, null, null, null, null, null, false,
      false);

  @Before
  public void setUp() throws Exception {
    this.secretDAO = new SecretDAO(
        dslContext,
        null,
        secretSeriesDAOFactory,
        null,
        null,
        permissionCheck);
  }

  @Test public void createSecretWithPrincipalCallsPermissionCheck() {
    doThrow(TestException.class).when(permissionCheck).checkAllowedOrThrow(client, Action.CREATE);

    assertThatThrownBy(() -> createSecretWithPrincipal(client)).isInstanceOf(TestException.class);
  }

  @Test public void createOrUpdateSecretWithPrincipalCallsPermissionCheck() {
    when(dslContext.configuration()).thenReturn(null);
    when(secretSeriesDAOFactory.using(any())).thenReturn(secretSeriesDAO);
    when(secretSeriesDAO.getSecretSeriesByName(anyString())).thenReturn(Optional.empty());
    doThrow(TestException.class).when(permissionCheck).checkAllowedOrThrow(client, Action.CREATE);

    assertThatThrownBy(() -> createOrUpdateSecretWithPrincipal(client)).isInstanceOf(TestException.class);
  }

  @Test public void getSecretByNameWithPrincipalCallsPermissionCheck() {
    when(dslContext.configuration()).thenReturn(null);
    when(secretSeriesDAOFactory.using(any())).thenReturn(secretSeriesDAO);
    when(secretSeriesDAO.getSecretSeriesByName(anyString())).thenReturn(Optional.empty());
    doThrow(TestException.class).when(permissionCheck).checkAllowedOrThrow(client, Action.READ, null);

    assertThatThrownBy(() -> secretDAO.getSecretByName("secretName", client)).isInstanceOf(TestException.class);
  }

  @Test public void getSecretsWithPrincipalCallsPermissionCheck() {
    doThrow(TestException.class).when(permissionCheck).checkAllowedOrThrow(client, Action.READ);

    assertThatThrownBy(() ->  secretDAO.getSecrets(null, null, null,
        null, null, client)).isInstanceOf(TestException.class);
  }

  @Test public void getSecretsBatchedWithPrincipalCallsPermissionCheck() {
    doThrow(TestException.class).when(permissionCheck).checkAllowedOrThrow(client, Action.READ);

    assertThatThrownBy(() ->  secretDAO.getSecretsBatched(0, 0, false,
        client)).isInstanceOf(TestException.class);
  }

  @Test public void deleteSecretsByNameWithPrincipalCallsPermissionCheck() {
    when(dslContext.configuration()).thenReturn(null);
    when(secretSeriesDAOFactory.using(any())).thenReturn(secretSeriesDAO);
    when(secretSeriesDAO.getSecretSeriesByName(anyString())).thenReturn(Optional.empty());
    doThrow(TestException.class).when(permissionCheck).checkAllowedOrThrow(client, Action.DELETE, null);

    assertThatThrownBy(() -> secretDAO.deleteSecretsByName("secretName", client))
        .isInstanceOf(TestException.class);
  }

  private long createSecretWithPrincipal(Object principal) {
    return secretDAO.createSecret(
        "secretName",
        "ownerName",
        "encryptedSecret",
        "hmac",
        "creator",
        NO_METADATA,
        0,
        "description",
        null,
        null,
        principal);
  }

  private long createOrUpdateSecretWithPrincipal(Object principal) {
    return secretDAO.createOrUpdateSecret(
        "secretName",
        "ownerName",
        "encryptedSecret",
        "hmac",
        "creator",
        NO_METADATA,
        0,
        "description",
        null,
        null,
        principal);
  }

  // Used to stop test after calling permission check
  private class TestException extends RuntimeException {}
}
