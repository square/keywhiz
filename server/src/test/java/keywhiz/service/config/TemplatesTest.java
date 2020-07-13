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

import com.google.common.io.Files;
import java.io.File;
import java.net.InetAddress;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Templates.class)
public class TemplatesTest {
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  private static String hostName;

  @BeforeClass public static void setUpOnce() throws Exception {
    hostName = InetAddress.getLocalHost().getHostName();
    assertThat(hostName).isNotEmpty();
  }

  @Test public void resolvesExternalReferences() throws Exception {
    File passwordFile = tempDir.newFile("GreenFlash.txt");
    Files.write("ZestyBrew", passwordFile, UTF_8);

    assertThat(Templates.evaluateExternal("external:" + passwordFile.getAbsolutePath()))
        .isEqualTo("ZestyBrew");
  }

  @Test public void resolvesNormalStrings() throws Exception {
    assertThat(Templates.evaluateExternal("FlatTire"))
        .isEqualTo("FlatTire");
  }

  @Test public void evaluatesHostNameTemplateHandlesHostNames() {
    String output = Templates.evaluateHostName("a-%hostname%-b");
    assertThat(output).isEqualTo(format("a-%s-b", hostName));
  }

  @Test public void evaluatesHostNameTemplateHandlesNormalStrings() {
    assertThat(Templates.evaluateHostName("boring")).isEqualTo("boring");
  }

  @Test public void evaluatesCustomPortTemplateHandlesCustomPortsWithVarSet() {
    mockStatic(System.class);
    expect(System.getenv(Templates.CUSTOM_MYSQL_PORT_ENV_VARIABLE)).andReturn("5555");
    replayAll();
    String output = Templates.evaluateCustomMysqlPort("(host=localhost)(%custom_mysql_port%)");
    verifyAll();
    assertThat(output).isEqualTo("(host=localhost)(port=5555)");
  }

  @Test public void evaluatesCustomPortTemplateHandlesCustomPortsWithoutVarSet() {
    mockStatic(System.class);
    expect(System.getenv(Templates.CUSTOM_MYSQL_PORT_ENV_VARIABLE)).andReturn(null);
    replayAll();
    String output = Templates.evaluateCustomMysqlPort("(host=localhost)(%custom_mysql_port%)");
    verifyAll();
    assertThat(output).isEqualTo("(host=localhost)()");
  }

  @Test public void evaluatesCustomPortTemplateHandlesNormalStrings() {
    assertThat(Templates.evaluateCustomMysqlPort("boring")).isEqualTo("boring");
  }
}
