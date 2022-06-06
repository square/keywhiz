package keywhiz.service.permissions;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class PermissionCheckModule extends AbstractModule {

  @Override
  protected void configure() {}

  @Provides
  public PermissionCheck createPermissionCheck(MetricRegistry metricRegistry) {
    PermissionCheck alwaysFail = new AlwaysFailPermissionCheck();
    PermissionCheck alwaysAllow = new AlwaysAllowDelegatingPermissionCheck(metricRegistry, alwaysFail);
    return alwaysAllow;
  }
}
