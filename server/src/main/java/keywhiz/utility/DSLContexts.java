/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  private DSLContexts() {}

  public static DSLContext databaseAgnostic(DataSource dataSource) throws SQLException {
    SQLDialect dialect;
    try (Connection conn = dataSource.getConnection()) {
      dialect = dialect(conn);
    }
    return DSL.using(dataSource, dialect, new Settings()
                                          .withRenderSchema(false)
                                          .withRenderNameStyle(RenderNameStyle.AS_IS));
    }
}
