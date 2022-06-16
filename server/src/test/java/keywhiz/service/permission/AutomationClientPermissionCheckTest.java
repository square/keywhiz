package keywhiz.service.permission;

import com.codahale.metrics.MetricRegistry;
import java.security.Principal;
import java.util.Objects;
import keywhiz.auth.User;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.AutomationClientPermissionCheck;
import keywhiz.service.permissions.KeywhizPrincipal;
import keywhiz.service.permissions.KeywhizPrincipalImpl;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutomationClientPermissionCheckTest {

  private MetricRegistry metricRegistry;
  private AutomationClientPermissionCheck automationCheck;

  private static Objects target;

  private static final String ISALLOWED_SUCCESS_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.success.histogram";
  private static final String ISALLOWED_FAILURE_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.failure.histogram";
  private static final String CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.success.histogram";
  private static final String CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME = "keywhiz.service.permissions.AutomationClientPermissionCheck.failure.histogram";

  private static final KeywhizPrincipal automationClient = new KeywhizPrincipalImpl(0,
      "automationClient", null, null, null, null, null, null, null, null, false,
      true) {
    @Override public String getName() {
      return null;
    }
  };
  private static final KeywhizPrincipal nonAutomationClient = new KeywhizPrincipalImpl(0,
      "noneAutomationClient", null, null, null, null, null, null, null, null, false,
      false) {
    @Override public String getName() {
      return null;
    }
  };

  private static final User user = User.named("user");
  // KeywhizPrincipal keywhizUser = (KeywhizPrincipal) user;

  @Before
  public void setUp() {
    metricRegistry = new MetricRegistry();
    automationCheck = new AutomationClientPermissionCheck(metricRegistry);
  }

  @Test public void testIsAllowedWithAutomationClient() {
    boolean permitted = automationCheck.isAllowed(automationClient, Action.ADD, target);

    assertThat(permitted);

    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1);

    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getSnapshot().getMean()).isEqualTo(0);
  }

  @Test public void testIsAllowedWithNonAutomationClient() {
    boolean permitted = automationCheck.isAllowed(nonAutomationClient, Action.ADD, target);

    assertThat(!permitted);

    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(0);

    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1);
  }
}
