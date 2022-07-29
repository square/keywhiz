package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.List;

public class PermissionCheckModule extends AbstractModule {

  @Override
  protected void configure() {}

  @Provides
  public PermissionCheck createPermissionCheck(MetricRegistry metricRegistry,
      AutomationClientPermissionCheck automationClientCheck,
      OwnershipPermissionCheck ownershipCheck) {
    PermissionCheck anyPermissionCheck = new AnyPermissionCheck(metricRegistry, List.of(ownershipCheck, automationClientCheck));
    return anyPermissionCheck;
  }
}
