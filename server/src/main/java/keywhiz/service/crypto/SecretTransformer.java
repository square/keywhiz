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
   * @param seriesAndContent a secret series and secret contents as stored in the database
   * @return the same information restructured as a Secret
   */
  public Secret transform(SecretSeriesAndContent seriesAndContent) {
    checkNotNull(seriesAndContent);
    SecretSeries series = seriesAndContent.series();
    SecretContent content = seriesAndContent.content();

    return new Secret(
        series.id(),
        series.name(),
        series.owner(),
        series.description(),
        () -> cryptographer.decrypt(content.encryptedContent()),
        content.hmac(),
        series.createdAt(),
        series.createdBy(),
        series.updatedAt(),
        series.updatedBy(),
        content.metadata(),
        series.type().orElse(null),
        series.generationOptions(),
        content.expiry(),
        series.currentVersion().orElse(null),
        content.createdAt(),
        content.createdBy());
  }
}
