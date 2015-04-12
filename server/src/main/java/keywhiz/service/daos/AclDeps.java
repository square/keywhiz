package keywhiz.service.daos;

import org.skife.jdbi.v2.sqlobject.CreateSqlObject;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

/**
 * Temporary class while we transition from JDBI to jooq. We need this because we can't use
 * dbi.ondemand + @Inject in the same class.
 */
@RegisterMapper({SecretSeriesMapper.class, GroupMapper.class, ClientMapper.class})
abstract public class AclDeps {
  @CreateSqlObject protected abstract ClientDAO createClientDAO();
  @CreateSqlObject protected abstract GroupDAO createGroupDAO();
  @CreateSqlObject protected abstract SecretContentDAO createSecretContentDAO();
  @CreateSqlObject protected abstract SecretSeriesDAO createSecretSeriesDAO();
}
