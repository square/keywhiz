package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import java.util.Objects;
import keywhiz.auth.User;
import keywhiz.api.model.Client;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AutomationClientPermissionCheckTest {

  private MetricRegistry metricRegistry;
  private AutomationClientPermissionCheck automationCheck;

  private static Objects target;

  private static final String ISALLOWED_SUCCESS_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.success.histogram";
  private static final String ISALLOWED_FAILURE_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.failure.histogram";
  private static final String CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.success.histogram";
  private static final String CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.failure.histogram";

  private static final Client
      automationClient = new Client(0, "automationClient", null, null, null, null, null, null, null, null, false,
      true);
  private static final Client
      nonAutomationClient = new Client(0, "nonAutomationClient", null, null, null, null, null, null, null, null, false,
      false);
  private static final User user = User.named("user");

  @Before
  public void setUp() {
    metricRegistry = new MetricRegistry();
    automationCheck = new AutomationClientPermissionCheck(metricRegistry);
  }

  @Test public void testIsAllowedWithAutomationClient() {
    boolean permitted = automationCheck.isAllowed(automationClient, Action.ADD, target);

    assertThat(permitted).isTrue();

    checkCorrectMetrics(1);
  }

  @Test public void testIsAllowedWithNonAutomationClient() {
    boolean permitted = automationCheck.isAllowed(nonAutomationClient, Action.ADD, target);

    assertThat(permitted).isFalse();

    checkCorrectMetrics(0);
  }

  @Test public void testIsAllowedWithUser() {
    automationCheck.isAllowed(user, Action.ADD, target);

    checkCorrectMetrics(0);
  }

  @Test public void testCheckAllowedOrThrowWithAutomationClient() {
    automationCheck.checkAllowedOrThrow(automationClient, Action.ADD, target);

    checkCorrectMetrics(1);

  }

  @Test public void testCheckAllowedOrThrowWithNonAutomationClient() {
    assertThatThrownBy(() -> automationCheck.checkAllowedOrThrow(nonAutomationClient, Action.ADD,
        target)).isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  @Test public void testCheckAllowedOrThrowWithUser() {
    assertThatThrownBy(() -> automationCheck.checkAllowedOrThrow(user, Action.ADD,
        target)).isInstanceOf(RuntimeException.class);

    checkCorrectMetrics(0);
  }

  private void checkCorrectMetrics(int isPermitted) {
    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(isPermitted);

    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1-isPermitted);
  }
}
