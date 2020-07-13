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

package keywhiz.service.config;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Templates {
  private static final Logger logger = LoggerFactory.getLogger(Templates.class);
  private static final String HOSTNAME_PLACEHOLDER = "%hostname%";
  private static final String CUSTOM_MYSQL_PORT_PLACEHOLDER = "%custom_mysql_port%";
  protected static final String CUSTOM_MYSQL_PORT_ENV_VARIABLE = "KEYWHIZ_CUSTOM_MYSQL_PORT";
  private static final String EXTERNAL_PREFIX = "external:";

  /**
   * Evaluates an optionally templated string.
   * <p>
   * If the template contains <code>%hostname%</code> the value is replaced by the current hostname
   * of the system.
   * <p>
   * If the template contains <code>%custom_mysql_port%</code> the value is replaced by the value of
   * the KEYWHIZ_CUSTOM_MYSQL_PORT environment variable.
   * <p>
   * If the template begins with <code>external:</code> the rest of the string is treated as a
   * filename to load and use as the return value.
   * <p>
   * For example, you may have a password you want to load at runtime from the filesystem,
   * <code>/tmp/foo1.example.com.txt</code>, which contains the string <code>bar</code> and the
   * current hostname is foo1.example.com. An input string with the external prefix and hostname
   * template, such as <code>external:/tmp/%hostname%.txt</code> and this will be converted to
   * <code>bar</code>.
   * <p>
   * If the input is just a normal string, the original value is returned.
   *
   * @param template the templated string
   * @return evaluated template
   * @throws IOException on error when using filesystem
   */
  public static String evaluateTemplate(String template) throws IOException {
    String hostReplaced = Templates.evaluateHostName(template);
    String hostAndPortReplaced = Templates.evaluateCustomMysqlPort(hostReplaced);
    return Templates.evaluateExternal(hostAndPortReplaced);
  }

  /**
   * Returns trimmed contents of a file when input string of form 'external:some/file/path' or just
   * the original input otherwise.
   *
   * @param template string which may optionally begin with 'external:'.
   * @return string with content of file when input templates, otherwise unchanged input.
   * @throws IOException on errors reading from filesystem.
   */
  public static String evaluateExternal(String template) throws IOException {
    if (template.startsWith(EXTERNAL_PREFIX)) {
      Path path = Path.of(template.substring(EXTERNAL_PREFIX.length()));
      logger.info("Reading configuration value from file {}", path);
      try {
        return Files.readString(path, UTF_8).trim();
      } catch (IOException exception) {
        logger.error("Error reading configuration value '{}':{}",
            template, exception);
        throw exception;
      }
    }

    return template;
  }

  /**
   * Replaces a single occurrence of <code>%hostname%</code> template with the current hostname.
   *
   * @param template string which may optionally contain '%hostname%' template.
   * @return string with '%hostname%' replaced with the current hostname, otherwise unchanged input.
   */
  public static String evaluateHostName(String template) {
    if (template.contains(HOSTNAME_PLACEHOLDER)) {
      String hostname;
      try {
        hostname = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        throw Throwables.propagate(e);
      }
      return template.replaceFirst(HOSTNAME_PLACEHOLDER, hostname);
    }
    return template;
  }

  /**
   * Replaces a single occurrence of <code>%custom_mysql_port%</code> template with "port=" followed
   * by the current value of the KEYWHIZ_CUSTOM_MYSQL_PORT environment variable. This can be used to
   * customize the MySQL port in a JDBC URL as described in https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html#connector-j-url-single-host-without-props.
   *
   * @param template string which may optionally contain '%custom_mysql_port%' template.
   * @return string with '%custom_mysql_port%' replaced with the current value of the
   * KEYWHIZ_CUSTOM_MYSQL_PORT environment variable, otherwise unchanged input.
   */
  public static String evaluateCustomMysqlPort(String template) {
    if (template.contains(CUSTOM_MYSQL_PORT_PLACEHOLDER)) {
      String customPort = System.getenv(CUSTOM_MYSQL_PORT_ENV_VARIABLE);
      if (!isNullOrEmpty(customPort)) {
        return template.replaceFirst(CUSTOM_MYSQL_PORT_PLACEHOLDER, format("port=%s", customPort));
      } else {
        return template.replaceFirst(CUSTOM_MYSQL_PORT_PLACEHOLDER, "");
      }
    }
    return template;
  }
}
