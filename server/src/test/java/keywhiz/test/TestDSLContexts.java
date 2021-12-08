package keywhiz.test;

import java.sql.Connection;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;

public final class TestDSLContexts {
  private TestDSLContexts() {}

  public static DSLContext returning(Record r) {
    MockDataProvider dataProvider = ignored -> new MockResult[] { new MockResult(r) };
    Connection connection = new MockConnection(dataProvider);
    DSLContext context = DSL.using(connection);
    return context;
  }
}
