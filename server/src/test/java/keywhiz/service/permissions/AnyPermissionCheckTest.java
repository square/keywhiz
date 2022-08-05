package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AnyPermissionCheckTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock private PermissionCheck delegate1;
  @Mock private PermissionCheck delegate2;

  private static Object source = new Object();
  private static Object target = new Object();

  private MetricRegistry metricRegistry;

  @Before
  public void setUp() {
    metricRegistry = new MetricRegistry();
  }

  @Test public void isAllowedReturnsFalseWithEmptyCheckList() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, Collections.emptyList());

    assertFalse(anyPermissionCheck.isAllowed(source, Action.ADD, target));
  }

  @Test public void isAllowedReturnsFalseWhenAllDelegatesReturnFalse() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, createDelegatesList(false, false));

    assertFalse(anyPermissionCheck.isAllowed(source, Action.ADD, target));
  }

  @Test public void isAllowedReturnsTrueWhenOneDelegateReturnsTrue() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, createDelegatesList(true, false));

    assertTrue(anyPermissionCheck.isAllowed(source, Action.ADD, target));
  }

  @Test public void checkAllowedOrThrowThrowsExceptionWithEmptyCheckList() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, Collections.emptyList());

    assertThatThrownBy(() -> anyPermissionCheck.checkAllowedOrThrow(source, Action.ADD, target))
        .isInstanceOf(RuntimeException.class);
  }

  @Test public void checkAllowedOrThrowThrowsExceptionWhenAllDelegatesReturnFalse() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, createDelegatesList(false, false));

    assertThatThrownBy(() -> anyPermissionCheck.checkAllowedOrThrow(source, Action.ADD, target))
        .isInstanceOf(RuntimeException.class);
  }

  @Test public void checkAllowedOrThrowReturnsVoidWhenOneDelegateReturnsTrue() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, createDelegatesList(true, false));

    anyPermissionCheck.checkAllowedOrThrow(source, Action.ADD, target);
  }

  private List<PermissionCheck> createDelegatesList(boolean delegate1Allowed, boolean delegate2Allowed) {
    when(delegate1.isAllowed(any(), any(), any())).thenReturn(delegate1Allowed);
    when(delegate2.isAllowed(any(), any(), any())).thenReturn(delegate2Allowed);
    return List.of(delegate1, delegate2);
  }
}
