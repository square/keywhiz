package keywhiz.service.permission;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableSet;
import java.util.Base64;
import keywhiz.api.ApiDate;
import keywhiz.api.model.Client;
import keywhiz.api.model.Group;
import keywhiz.api.model.Secret;
import keywhiz.auth.User;
import keywhiz.service.daos.AclDAO;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.OwnershipPermissionCheck;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public class OwnershipPermissionCheckTest {
  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock AclDAO.AclDAOFactory aclDAOFactory;
  @Mock AclDAO aclDAO;

  private MetricRegistry metricRegistry;
  private OwnershipPermissionCheck ownershipPermissionCheck;

  private static final ApiDate NOW = ApiDate.now();
  private static final Secret.LazyString ENCRYPTED_SECRET = () -> Base64.getUrlEncoder().encodeToString("content".getBytes(UTF_8));

  private static final Secret SECRET_WITH_OWNER = new Secret(0, "secret.with.owner", "owner", null,
      ENCRYPTED_SECRET, "checksum", NOW, null, NOW, null,
      null, null, null, 0, null, null, null);
  private static final Secret SECRET_WITHOUT_OWNER = new Secret(1, "secret.without.owner", null, null,
      ENCRYPTED_SECRET, "checksum", NOW, null, NOW, null,
      null, null, null, 0, null, null, null);

  private static final Client CLIENT = new Client(0, "client", null, null, null,
      null, null, null, null, null, false, true);
  private static final User USER = User.named("user");

  private static final Group SECRET_OWNER_GROUP = new Group(0, "owner", null, NOW, null, NOW, null, null);
  private static final Group NOT_SECRET_OWNER_GROUP = new Group(1, "not-owner", null, NOW, null, NOW, null, null);

  private static final String SUCCESS_METRIC_NAME = MetricRegistry.name(OwnershipPermissionCheck.class, "success", "histogram");
  private static final String FAILURE_METRIC_NAME = MetricRegistry.name(OwnershipPermissionCheck.class, "failure", "histogram");

  @Before
  public void setUp() {
    metricRegistry = new MetricRegistry();
    when(aclDAOFactory.readwrite()).thenReturn(aclDAO);
    ownershipPermissionCheck = new OwnershipPermissionCheck(metricRegistry, aclDAOFactory);
  }

  @Test
  public void testIsAllowedWhenSourceIsNotAClient() {
    boolean permitted = ownershipPermissionCheck.isAllowed(USER, Action.CREATE, SECRET_WITH_OWNER);

    assertThat(permitted).isFalse();
    checkCorrectMetrics(0);
  }

  @Test
  public void testIsAllowedWhenTargetIsNotASecret() {
    boolean permitted = ownershipPermissionCheck.isAllowed(CLIENT, Action.CREATE, "not a secret");

    assertThat(permitted).isFalse();
    checkCorrectMetrics(0);
  }

  @Test
  public void testIsAllowedWhenSourceIsNotAClientAndTargetIsNotASecret() {
    boolean permitted = ownershipPermissionCheck.isAllowed(USER, Action.CREATE, "not a secret");

    assertThat(permitted).isFalse();
    checkCorrectMetrics(0);
  }

  @Test
  public void testIsAllowedWhenClientHasNoGroup() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of());

    boolean permitted = ownershipPermissionCheck.isAllowed(CLIENT, Action.CREATE, SECRET_WITH_OWNER);

    assertThat(permitted).isFalse();
    checkCorrectMetrics(0);
  }

  @Test
  public void testIsAllowedWhenClientDoesNotBelongToSecretOwnerGroup() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of(NOT_SECRET_OWNER_GROUP));

    boolean permitted = ownershipPermissionCheck.isAllowed(CLIENT, Action.CREATE, SECRET_WITH_OWNER);

    assertThat(permitted).isFalse();
    checkCorrectMetrics(0);
  }

  @Test
  public void testIsAllowedWhenSecretHasNoOwner() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of(NOT_SECRET_OWNER_GROUP, SECRET_OWNER_GROUP));

    boolean permitted = ownershipPermissionCheck.isAllowed(CLIENT, Action.CREATE, SECRET_WITHOUT_OWNER);

    assertThat(permitted).isFalse();
    checkCorrectMetrics(0);
  }

  @Test
  public void testIsAllowedWhenClientBelongsToSecretOwnerGroup() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of(NOT_SECRET_OWNER_GROUP, SECRET_OWNER_GROUP));

    boolean permitted = ownershipPermissionCheck.isAllowed(CLIENT, Action.CREATE, SECRET_WITH_OWNER);

    assertThat(permitted).isTrue();
    checkCorrectMetrics(1);
  }

  @Test
  public void testCheckAllowedOrThrowWhenSourceIsNotAClient() {
    assertThatThrownBy(() -> ownershipPermissionCheck.checkAllowedOrThrow(USER, Action.CREATE, SECRET_WITH_OWNER)).
        isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test
  public void testCheckAllowedOrThrowWhenTargetIsNotASecret() {
    assertThatThrownBy(() -> ownershipPermissionCheck.checkAllowedOrThrow(CLIENT, Action.CREATE, "not a secret")).
        isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test
  public void testCheckAllowedOrThrowWhenSourceIsNotAClientAndTargetIsNotASecret() {
    assertThatThrownBy(() -> ownershipPermissionCheck.checkAllowedOrThrow(USER, Action.CREATE, "not a secret")).
        isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test
  public void testCheckAllowedOrThrowWhenClientHasNoGroup() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of());

    assertThatThrownBy(() -> ownershipPermissionCheck.checkAllowedOrThrow(CLIENT, Action.CREATE, SECRET_WITH_OWNER)).
        isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test
  public void testCheckAllowedOrThrowWhenClientDoesNotBelongToSecretOwnerGroup() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of(NOT_SECRET_OWNER_GROUP));

    assertThatThrownBy(() -> ownershipPermissionCheck.checkAllowedOrThrow(CLIENT, Action.CREATE, SECRET_WITH_OWNER)).
        isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test
  public void testCheckAllowedOrThrowWhenSecretHasNoOwner() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of(NOT_SECRET_OWNER_GROUP, SECRET_OWNER_GROUP));

    assertThatThrownBy(() -> ownershipPermissionCheck.checkAllowedOrThrow(CLIENT, Action.CREATE, SECRET_WITHOUT_OWNER)).
        isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test
  public void testCheckAllowedOrThrowWhenClientBelongsToSecretOwnerGroup() {
    when(aclDAO.getGroupsFor(CLIENT)).thenReturn(ImmutableSet.of(NOT_SECRET_OWNER_GROUP, SECRET_OWNER_GROUP));

    ownershipPermissionCheck.checkAllowedOrThrow(CLIENT, Action.CREATE, SECRET_WITH_OWNER);

    checkCorrectMetrics(1);
  }

  private void checkCorrectMetrics(int isPermitted) {
    assertThat(metricRegistry.histogram(SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(isPermitted);

    assertThat(metricRegistry.histogram(FAILURE_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(FAILURE_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1-isPermitted);
  }
}
