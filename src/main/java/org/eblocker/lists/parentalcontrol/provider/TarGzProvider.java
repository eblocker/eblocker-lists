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

import org.eblocker.lists.parentalcontrol.DomainBlacklist;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import org.eblocker.lists.util.HttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.ByteStreams;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class TarGzProvider implements BlacklistProvider {
    private static final Logger log = LoggerFactory.getLogger(TarGzProvider.class);
    private final HttpClient httpClient;

    public TarGzProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public DomainBlacklist createBlacklist(JsonNode sourceParameters) {
        String url = sourceParameters.get("url").asText();
        String path = sourceParameters.get("path").asText();

        log.debug("downloading {}", url);
        try (TarArchiveInputStream in = new TarArchiveInputStream(new GZIPInputStream(httpClient.download(url, "", "")))) {
            TarArchiveEntry entry;
            while((entry = in.getNextTarEntry()) != null && !entry.getName().equals(path)) {
                log.debug("{}: {}", entry.getName(), entry.getSize());
            }

            if (entry == null) {
                throw new IOException("failed to find " + path);
            }

            log.debug("{}: {} (found)", entry.getName(), entry.getSize());
            byte[] content = new byte[(int)entry.getSize()];
            ByteStreams.readFully(in, content);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content)))) {
                Set<String> domains = br.lines()
                        .map(String::trim)
                        .filter(line -> !line.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                        .map(line -> "." + line)
                        .collect(Collectors.toSet());
                log.debug("extracted {} domains from {}", domains.size(), path);
                return new DomainBlacklist(LocalDateTime.ofInstant(entry.getLastModifiedDate().toInstant(), ZoneId.of("UTC")), domains);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
