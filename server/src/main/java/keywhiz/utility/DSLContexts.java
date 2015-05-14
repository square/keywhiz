package keywhiz.utility;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameStyle;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import static org.jooq.tools.jdbc.JDBCUtils.dialect;

/**
 * Returns a DSLContext which works with postgres, mysql, h2, etc.
 *
 * In theory we shouldn't need to do this. Any suggestions to remove this code is welcome!
 */
public class DSLContexts {
  public static DSLContext databaseAgnostic(DataSource dataSource) throws SQLException {
    Connection conn = dataSource.getConnection();
    if (dialect(conn) == SQLDialect.H2) {
      return DSL.using(dataSource, SQLDialect.H2,
          new Settings()
              .withRenderSchema(false)
              .withRenderNameStyle(RenderNameStyle.AS_IS));
    }
    return DSL.using(conn);
  }
}
