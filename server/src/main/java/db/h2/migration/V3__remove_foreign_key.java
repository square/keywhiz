package db.h2.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

public class V3__remove_foreign_key implements JdbcMigration {
  public void migrate(Connection connection) throws Exception {
    String find = "select CONSTRAINT_TYPE, CONSTRAINT_NAME, TABLE_NAME from information_schema.constraints";
    ResultSet constraints = connection.createStatement().executeQuery(find);
    while  (constraints.next()) {
      String type = constraints.getString("CONSTRAINT_TYPE");
      String constraint = constraints.getString("CONSTRAINT_NAME");
      String table = constraints.getString("TABLE_NAME");
      if(type == "REFERENTIAL") {
        connection.createStatement().executeUpdate("ALTER TABLE " + table + " DROP CONSTRAINT " + constraint);
        System.out.printf("Found constraint (%s, %s, %s)", type, constraint, table);
        System.out.println(" - Dropped it.");
      }
    }
  }
}
