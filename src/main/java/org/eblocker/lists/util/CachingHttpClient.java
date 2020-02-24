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

import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CachingHttpClient implements HttpClient {

    private static final Logger log = LoggerFactory.getLogger(CachingHttpClient.class);

    private final Map<String, byte[]> cache = new HashMap<>();
    private final HttpClient httpClient;

    public CachingHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public InputStream download(String url, String username, String password) throws IOException {
        byte[] content = cache.get(url);

        if (content == null) {
            log.debug("downloading {}", url);
            try (InputStream in = httpClient.download(url, username, password)) {
                content = ByteStreams.toByteArray(in);
                log.debug("caching {}kb for {}", content.length / 1024, url);
                cache.put(url, content);
                log.debug("total cache size: {}kb", cache.values().stream().map(c -> c.length).reduce(Integer::sum).get() / 1024);
            }
        } else {
            log.debug("returning cached content for {}", url);
        }

        return new ByteArrayInputStream(content);
    }
}
