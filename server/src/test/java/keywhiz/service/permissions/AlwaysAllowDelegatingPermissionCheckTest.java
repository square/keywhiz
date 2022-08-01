package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class AlwaysAllowDelegatingPermissionCheckTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Mock PermissionCheck delegate;

  private MetricRegistry metricRegistry;
  private AlwaysAllowDelegatingPermissionCheck alwaysAllow;

  private static Objects target;
  private static Objects principal;

  private static final String ISALLOWED_SUCCESS_METRIC_NAME = "keywhiz.service.permissions.AlwaysAllowDelegatingPermissionCheck.success.histogram";
  private static final String ISALLOWED_FAILURE_METRIC_NAME = "keywhiz.service.permissions.AlwaysAllowDelegatingPermissionCheck.failure.histogram";
  private static final String CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME = "keywhiz.service.permissions.AlwaysAllowDelegatingPermissionCheck.success.histogram";
  private static final String CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME = "keywhiz.service.permissions.AlwaysAllowDelegatingPermissionCheck.failure.histogram";

  @Before
  public void setUp() {
    metricRegistry = new MetricRegistry();
    alwaysAllow = new AlwaysAllowDelegatingPermissionCheck(metricRegistry, delegate);
  }

  @Test public void isAllowedReturnsTrueWhenDelegateReturnsTrue() {
    when(delegate.isAllowed(any(), any(), any())).thenReturn(true);

    boolean permitted = alwaysAllow.isAllowed(principal, Action.ADD, target);

    assertThat(permitted).isTrue();

    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1);

    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getSnapshot().getMean()).isEqualTo(0);
  }

  @Test public void isAllowedReturnsTrueWhenDelegateReturnsFalse() {
    when(delegate.isAllowed(any(), any(), any())).thenReturn(false);

    boolean permitted = alwaysAllow.isAllowed(principal, Action.ADD, target);

    assertThat(permitted).isTrue();

    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(0);

    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(ISALLOWED_FAILURE_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1);
  }

  @Test public void CheckAllowedOrThrowReturnsVoidWhenDelegateReturnsVoid() {
    doNothing().when(delegate).checkAllowedOrThrow(any(), any(), any());

    alwaysAllow.checkAllowedOrThrow(principal, Action.ADD, target);

    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1);

    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME).getSnapshot().getMean()).isEqualTo(0);
  }

  @Test public void CheckAllowedOrThrowReturnsVoidWhenDelegateThrowException() {
    doThrow(RuntimeException.class).when(delegate).checkAllowedOrThrow(any(), any(), any());

    alwaysAllow.checkAllowedOrThrow(principal, Action.ADD, target);

    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_SUCCESS_METRIC_NAME).getSnapshot().getMean()).isEqualTo(0);

    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME).getCount()).isEqualTo(1);
    assertThat(metricRegistry.histogram(CHECKALLOWEDORTHROW_EXCEPTION_METRIC_NAME).getSnapshot().getMean()).isEqualTo(1);
  }
}

