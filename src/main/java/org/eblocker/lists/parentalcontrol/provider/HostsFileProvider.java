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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HostsFileProvider implements BlacklistProvider {
    private static final Logger log = LoggerFactory.getLogger(ListProvider.class);
    private static final Pattern PATTERN = Pattern.compile("^\\d+.\\d+.\\d+.\\d+\\s+(.*)$");

    private final HttpClient httpClient;

    public HostsFileProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public DomainBlacklist createBlacklist(JsonNode sourceParameters) {
        String url = sourceParameters.get("url").asText();
        log.info("downloading {}", url);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(httpClient.download(url, "", "")))) {
            Set<String> domains = br.lines()
                    .map(line -> line.replaceAll("#.*", ""))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(m -> m.group(1))
                    .filter(domain -> !"localhost".equalsIgnoreCase(domain))
                    .map(domain -> "." + domain)
                    .collect(Collectors.toSet());
            log.info("extracted {} domains", domains.size());
            return new DomainBlacklist(LocalDateTime.now(), domains);
        } catch (IOException e) {
            throw new UncheckedIOException("downloading " + url + " failed", e);
        }
    }

}
