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

package keywhiz;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import java.io.File;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileAssetServletTest {
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  @Test public void loadsIndex() throws Exception {
    File folder = tempDir.newFolder("loadsIndexTest");
    File indexFile = tempDir.newFile("loadsIndexTest/index.html");
    Files.write("loadsIndexContent", indexFile, UTF_8);

    FileAssetServlet servlet = new FileAssetServlet(folder, "/ui/", "index.html");
    ByteSource byteSource = servlet.loadAsset("/ui/");
    assertThat(byteSource.read()).isEqualTo(Files.toByteArray(indexFile));
  }

  @Test public void notFoundWhenNoIndexFile() throws Exception {
    File folder = tempDir.newFolder("notFoundWhenNoIndexFileTest");

    FileAssetServlet servlet = new FileAssetServlet(folder, "/ui/", null);
    assertThat(servlet.loadAsset("/ui/")).isNull();
  }

  @Test public void loadsAsset() throws Exception {
    File folder = tempDir.newFolder("loadsAssetTest");
    File assetFile = tempDir.newFile("loadsAssetTest/asset.txt");
    Files.write("loadsAssetContent", assetFile, UTF_8);

    FileAssetServlet servlet = new FileAssetServlet(folder, "/ui/", "index.html");
    ByteSource byteSource = servlet.loadAsset("/ui/asset.txt");
    assertThat(byteSource.read()).isEqualTo(Files.toByteArray(assetFile));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsDirectoryTraversal() throws Exception {
    FileAssetServlet servlet = new FileAssetServlet(tempDir.getRoot(), "/ui/", "index.html");
    servlet.loadAsset("/ui/../../../../../../../etc/password");
  }

  @Test public void notFoundRequest() throws Exception {
    File folder = tempDir.newFolder("notFoundRequestTest");
    FileAssetServlet servlet = new FileAssetServlet(folder, "/ui/", "index.html");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getRequestURI()).thenReturn("/ui/non-existant");
    servlet.doGet(request, response);
    verify(response).sendError(HttpStatus.SC_NOT_FOUND);
  }
}
