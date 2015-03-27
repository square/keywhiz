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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.net.MediaType;
import io.dropwizard.servlets.assets.AssetServlet;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.MimeTypes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Serves static assets from the filesystem.
 *
 * This is heavily based on {@link AssetServlet} but does not load resources nor cache assets.
 * THIS CLASS SHOULD NOT BE USED IN PRODUCTION, primarily for performance reasons.
 */
public class FileAssetServlet extends HttpServlet {
  private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.HTML_UTF_8;
  private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  private final File assetDirectory;
  private final String uriPath;

  private final String indexFile;
  private final transient MimeTypes mimeTypes;

  /**
   * Creates a new {@code FileAssetServlet} that serves static assets loaded from {@code filePath}.
   * The assets are served at URIs rooted at {@code uriPath}. For example, given a {@code filePath}
   * of {@code "/data/assets"} and a {@code uriPath} of {@code "/js"}, a {@code FileAssetServlet}
   * would serve the contents of {@code /data/assets/example.js} in response to a request for
   * {@code /js/example.js}. If a directory is requested and {@code indexFile} is defined, then
   * {@code FileAssetServlet} will attempt to serve a file with that name in that directory. If a
   * directory is requested and {@code indexFile} is null, it will serve a 404.
   *
   * @param assetDirectory the base directory from which assets are loaded
   * @param uriPath the URI path fragment in which all requests are rooted
   * @param indexFile the filename to use when directories are requested, or null to serve no
   * indexes
   */
  public FileAssetServlet(File assetDirectory, String uriPath, @Nullable String indexFile) {
    checkArgument(assetDirectory.exists());
    this.assetDirectory = assetDirectory;
    this.uriPath = checkNotNull(uriPath);
    this.indexFile = indexFile;
    this.mimeTypes = new MimeTypes();
  }

  @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      ByteSource asset = loadAsset(req.getRequestURI());
      if (asset == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      final String mimeTypeOfExtension = req.getServletContext()
          .getMimeType(req.getRequestURI());
      MediaType mediaType = DEFAULT_MEDIA_TYPE;

      if (mimeTypeOfExtension != null) {
        try {
          mediaType = MediaType.parse(mimeTypeOfExtension);
          if (mediaType.is(MediaType.ANY_TEXT_TYPE)) {
            mediaType = mediaType.withCharset(DEFAULT_CHARSET);
          }
        } catch (IllegalArgumentException ignore) {}
      }

      resp.setContentType(mediaType.type() + "/" + mediaType.subtype());

      if (mediaType.charset().isPresent()) {
        resp.setCharacterEncoding(mediaType.charset().get().toString());
      }

      try (OutputStream output = resp.getOutputStream()) {
        asset.copyTo(output);
      }
    } catch (RuntimeException ignored) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @VisibleForTesting ByteSource loadAsset(String key) throws IOException {
    String requestedUrlPath = CharMatcher.is('/').trimFrom(key.substring(uriPath.length()));
    File requestedFile = new File(assetDirectory, requestedUrlPath);
    checkArgument(requestedFile.getCanonicalPath().startsWith(assetDirectory.getCanonicalPath()),
        "Requested file %s must be a child of assetDirectory %s", requestedFile, assetDirectory);

    if (requestedFile.isDirectory()) {
      if (indexFile != null) {
        return Files.asByteSource(new File(requestedFile, indexFile));
      } else {
        return null; // directory requested but no index file defined
      }
    }

    return Files.asByteSource(requestedFile);
  }
}
