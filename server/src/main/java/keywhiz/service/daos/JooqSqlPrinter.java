package keywhiz.service.daos;

import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;

// For testing/debugging only. To activate:
// JooqSqlPrinter.install(dslContext);
class JooqSqlPrinter extends DefaultExecuteListener {
  private static final Settings PRETTY_PRINT = new Settings().withRenderFormatted(true);

  @Override
  public void executeStart(ExecuteContext executeContext) {
    DSLContext dslContext = DSL.using(executeContext.dialect(), PRETTY_PRINT);

    if (executeContext.query() != null) {
      System.out.println(dslContext.renderInlined(executeContext.query()));
    }
  }

  public static void install(DSLContext dslContext) {
    dslContext.configuration().set(new DefaultExecuteListenerProvider(new JooqSqlPrinter()));
  }
}
