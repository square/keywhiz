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

package keywhiz.service.crypto;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import keywhiz.api.model.Secret;
import keywhiz.api.model.SecretContent;
import keywhiz.api.model.SecretSeries;
import keywhiz.api.model.SecretSeriesAndContent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Transforms DB content to Secret model, performing crypto when needed.
 */
public class SecretTransformer {
  private final ContentCryptographer cryptographer;

  @Inject public SecretTransformer(ContentCryptographer cryptographer) {
    this.cryptographer = cryptographer;
  }

  /**
   * Transform DB content to a Secret model.
   */
  public Secret transform(SecretSeriesAndContent seriesAndContent) {
    checkNotNull(seriesAndContent);
    SecretSeries series = seriesAndContent.series();
    SecretContent content = seriesAndContent.content();

    final String secretContent = cryptographer.decrypt(content.encryptedContent());

    return new Secret(
        series.id(),
        series.name(),
        content.version().orElse(""),
        series.description(),
        secretContent,
        content.createdAt(),
        content.createdBy(),
        content.updatedAt(),
        content.updatedBy(),
        content.metadata(),
        series.type().orElse(null),
        series.generationOptions());
  }

  /**
   * Transform a list of DB content to Secret models.
   */
  public List<Secret> transform(List<SecretSeriesAndContent> seriesAndContents) {
    return seriesAndContents.stream().map(this::transform).collect(Collectors.toList());
  }
}
