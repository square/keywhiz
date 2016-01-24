package db.h2.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V3__remove_foreign_key implements JdbcMigration {
  private static final Logger logger = LoggerFactory.getLogger(V3__remove_foreign_key.class);
  public void migrate(Connection connection) throws Exception {
    String find = "select CONSTRAINT_TYPE, CONSTRAINT_NAME, TABLE_NAME from information_schema.constraints";
    ResultSet constraints = connection.createStatement().executeQuery(find);
    while  (constraints.next()) {
      String type = constraints.getString("CONSTRAINT_TYPE");
      String constraint = constraints.getString("CONSTRAINT_NAME");
      String table = constraints.getString("TABLE_NAME");
      if(type == "REFERENTIAL") {
        connection.createStatement().executeUpdate("ALTER TABLE " + table + " DROP CONSTRAINT " + constraint);
        logger.info(String.format("Found constraint (%s, %s, %s)", type, constraint, table));
        logger.info(" - Dropped it.");
      }
    }
  }
}
