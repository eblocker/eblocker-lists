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
package org.eblocker.lists.parentalcontrol.provider;

import org.eblocker.server.common.util.ByteArrays;
import org.eblocker.server.icap.filter.bpjm.BpjmEntry;
import org.eblocker.server.icap.filter.bpjm.BpjmModul;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.lists.bpjm.BpjmModulZipReader;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import org.eblocker.lists.parentalcontrol.HashBlacklist;
import org.eblocker.lists.util.Digests;
import org.eblocker.lists.util.HttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BpjmListProvider implements BlacklistProvider {
    private static final Logger log = LoggerFactory.getLogger(BpjmListProvider.class);

    private static final byte[] EMPTY_STRING_MD5 = Digests.md5("");

    private final Properties properties;
    private final BpjmModulZipReader bpjmModulZipReader;
    private final HttpClient httpClient;

    public BpjmListProvider(Properties properties, BpjmModulZipReader bpjmModulZipReader, HttpClient httpClient) {
        this.properties = properties;
        this.bpjmModulZipReader = bpjmModulZipReader;
        this.httpClient = httpClient;
    }

    @Override
    public HashBlacklist createBlacklist(JsonNode sourceParameters) {
        try {
            InputStream in;
            String url = properties.getProperty("bpjm.url");
            if (url != null) {
                in = httpClient.download(url, "", "");
            } else {
                String path = properties.getProperty("bpjm.path");
                if (path != null) {
                    SimpleResource resource = new SimpleResource(path);
                    in = ResourceHandler.getInputStream(resource);
                } else {
                    throw new IOException("bpfm properties needs either a url or path");
                }
            }

            String hashFunctionName = properties.getProperty("bpjm.hashFunctionName");

            BpjmModul bpjmModul = bpjmModulZipReader.read(in);
            log.info("{} bpjm entries", bpjmModul.getEntries().size());
            Set<byte[]> domainsWithoutPath = bpjmModul.getEntries().stream()
                    .filter(e -> e.getDepth() == 0)
                    .filter(e -> Arrays.equals(e.getPathHash(), EMPTY_STRING_MD5))
                    .map(BpjmEntry::getDomainHash)
                    .collect(Collectors.toCollection(() -> new TreeSet<>(ByteArrays::compare)));
            log.info("{} domain only entries", domainsWithoutPath.size());
            return new HashBlacklist(LocalDateTime.ofInstant(Instant.ofEpochMilli(bpjmModul.getLastModified()), ZoneId.systemDefault()), domainsWithoutPath, hashFunctionName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
