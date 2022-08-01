package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AlwaysFailPermissionCheckTest {
  MetricRegistry metricRegistry;
  AlwaysFailPermissionCheck alwaysFail;
  Objects target;
  Objects principal;

  @Before
  public void setUp() {
    alwaysFail = new AlwaysFailPermissionCheck();
  }

  @Test
  public void isAllowedReturnsFalse() {
    boolean permitted = alwaysFail.isAllowed(principal, Action.ADD, target);
    assertThat(permitted).isFalse();
  }

  @Test
  public void checkAllowedOrThrowThrowsException() {
    assertThatThrownBy(() -> {alwaysFail.checkAllowedOrThrow(principal, Action.ADD, target);}).isInstanceOf(RuntimeException.class);
  }
}
