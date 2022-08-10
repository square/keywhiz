package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.List;

public class PermissionCheckModule extends AbstractModule {
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
}
