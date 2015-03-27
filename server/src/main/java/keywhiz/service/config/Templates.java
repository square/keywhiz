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
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Templates {
  private static final Logger logger = LoggerFactory.getLogger(Templates.class);
  private static final String HOSTNAME_PLACEHOLDER = "%hostname%";
  private static final String EXTERNAL_PREFIX = "external:";

  /**
   * Evaluates an optionally templated string.
   *
   * If the template contains <code>%hostname%</code> the value is replaced by the current hostname of
   * the system.
   *
   * If the template begins with <code>external:</code> the rest of the string is treated as a filename
   * to load and use as the return value.
   *
   * For example, you may have a password you want to load at runtime from the filesystem,
   * <code>/tmp/foo1.example.com.txt</code>, which contains the string <code>bar</code> and the
   * current hostname is foo1.example.com. An input string with the external prefix and hostname
   * template, such as <code>external:/tmp/%hostname%.txt</code> and this will be converted to
   * <code>bar</code>.
   *
   * If the input is just a normal string, the original value is returned.
   *
   * @param template the templated string
   * @return evaluated template
   * @throws IOException on error when using filesystem
   */
  public static String evaluateTemplate(String template) throws IOException {
    return Templates.evaluateExternal(Templates.evaluateHostName(template));
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
      File file = new File(template.substring(EXTERNAL_PREFIX.length()));
      logger.info("Reading configuration value from file {}", file.getPath());
      try {
        return Files.toString(file, UTF_8).trim();
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
}
