package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.List;

public class PermissionCheckModule extends AbstractModule {

  @Override
  protected void configure() {
    bindToDefaultConstructor(AllowClientSecretCreationPermissionCheck.class);
  }

  @Provides
  public PermissionCheck createPermissionCheck(MetricRegistry metricRegistry,
      AutomationClientPermissionCheck automationClientCheck,
      OwnershipPermissionCheck ownershipCheck,
      AllowClientSecretCreationPermissionCheck clientSecretCreationCheck) {

    List<PermissionCheck> permissionChecks = List.of(
        ownershipCheck,
        clientSecretCreationCheck,
        automationClientCheck
    );

    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, permissionChecks);
    return anyPermissionCheck;
  }

  private <T> void bindToDefaultConstructor(Class<T> clazz) {
    try {
      bind(clazz).toConstructor(clazz.getConstructor());
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
