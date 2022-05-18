package keywhiz.service.permission;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import java.security.Principal;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.AlwaysAllowPermissionCheck;
import keywhiz.service.permissions.AlwaysFailPermissionCheck;
import keywhiz.service.permissions.KeywhizPrincipal;
import keywhiz.service.permissions.PermissionCheck;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;

public class PermissionCheckTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock MetricRegistry metricRegistry;
  @Mock AlwaysFailPermissionCheck delegate;
  @Mock KeywhizPrincipal principal;
  @Mock Object target;
  PermissionCheck AlwaysAllowPermissionCheck;
  Action add;

  @Before
  public void setUp() {
    AlwaysAllowPermissionCheck = new AlwaysAllowPermissionCheck(metricRegistry, delegate);
  }

  @Test public void AlwaysAllowPermissionCheckReturnsTrue() {
    boolean permitted = AlwaysAllowPermissionCheck.isAllowed(principal, add.toString(), target);
    assertThat(permitted).isEqualTo(true);
  }
}

