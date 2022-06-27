package keywhiz.service.permission;

import java.util.Collections;
import java.util.List;
import keywhiz.service.permissions.Action;
import keywhiz.service.permissions.AnyPermissionCheck;
import keywhiz.service.permissions.PermissionCheck;
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

  private static Object source;
  private static Object target;

  @Test public void isAllowedReturnsTrueWithEmptyCheckList() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(Collections.emptyList());

    assertFalse(anyPermissionCheck.isAllowed(source, Action.ADD, target));
  }

  @Test public void isAllowedReturnsFalseWhenAllDelegatesReturnFalse() {
    when(delegate1.isAllowed(any(), any(), any())).thenReturn(false);
    when(delegate2.isAllowed(any(), any(), any())).thenReturn(false);
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(List.of(delegate1, delegate2));

    assertFalse(anyPermissionCheck.isAllowed(source, Action.ADD, target));
  }

  @Test public void isAllowedReturnsTrueWhenOneDelegateReturnsTrue() {
    when(delegate1.isAllowed(any(), any(), any())).thenReturn(true);
    when(delegate2.isAllowed(any(), any(), any())).thenReturn(false);
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(List.of(delegate1, delegate2));

    assertTrue(anyPermissionCheck.isAllowed(source, Action.ADD, target));
  }

  @Test public void checkAllowedOrThrowThrowsExceptionWithEmptyCheckList() {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(Collections.emptyList());

    assertThatThrownBy(() -> anyPermissionCheck.checkAllowedOrThrow(source, Action.ADD, target))
        .isInstanceOf(RuntimeException.class);
  }

  @Test public void checkAllowedOrThrowThrowsExceptionWhenAllDelegatesReturnFalse() {
    when(delegate1.isAllowed(any(), any(), any())).thenReturn(false);
    when(delegate2.isAllowed(any(), any(), any())).thenReturn(false);
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(List.of(delegate1, delegate2));

    assertThatThrownBy(() -> anyPermissionCheck.checkAllowedOrThrow(source, Action.ADD, target))
        .isInstanceOf(RuntimeException.class);
  }

  @Test public void checkAllowedOrThrowReturnsVoidWhenOneDelegateReturnsTrue() {
    when(delegate1.isAllowed(any(), any(), any())).thenReturn(true);
    when(delegate2.isAllowed(any(), any(), any())).thenReturn(false);
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(List.of(delegate1, delegate2));

    anyPermissionCheck.isAllowed(source, Action.ADD, target);
  }
}
