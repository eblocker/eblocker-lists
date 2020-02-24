/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.lists.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientFactory {

    private static final Logger log = LoggerFactory.getLogger(HttpClientFactory.class);

    public static HttpClient create() {
        String httpCachePath = System.getProperty("httpCachePath");
        if (httpCachePath != null) {
            log.info("using on-disk-caching http-client: {}", httpCachePath);
            return new OnDiskCachingHttpClient(httpCachePath, new DefaultHttpClient());
        }
        log.info("using in-memory caching http-client");
        return new CachingHttpClient(new DefaultHttpClient());
    }

}
