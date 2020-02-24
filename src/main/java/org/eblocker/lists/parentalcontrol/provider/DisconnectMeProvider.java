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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Parses the services.json file published by Disconnect.me:
 * https://github.com/disconnectme/disconnect-tracking-protection
 */
public class DisconnectMeProvider implements BlacklistProvider {
    private static final Logger log = LoggerFactory.getLogger(DisconnectMeProvider.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DisconnectMeProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public DomainBlacklist createBlacklist(JsonNode sourceParameters) {
        String url = sourceParameters.get("url").asText();

        Set<String> domains = new HashSet<>();
        try {
            InputStream in = httpClient.download(url, "", "");
            log.info("downloading {}", url);

            /*
            Parsing the services.json file:
            Unfortunately, the JSON structure is deeply nested and the keys are
            mainly names and URLs, so it is not a good fit for the ObjectMapper.
            Here we "manually" iterate the JsonNodes to get to the domains.
             */
            JsonNode doc = objectMapper.readValue(in, JsonNode.class);
            JsonNode categories = sourceParameters.get("categories");
            for (JsonNode category : categories) {
                log.info("Adding category '{}'", category.asText());
                JsonNode services = doc.get("categories").get(category.asText());
                for (JsonNode service : services) {
                    for (JsonNode serviceData : service) {
                        for (JsonNode domainListOrParameter : serviceData) {
                            if (domainListOrParameter.isArray()) {
                                for (JsonNode domain : domainListOrParameter) {
                                    domains.add(domain.asText());
                                }
                            }
                        }
                    }
                }
            }
            return new DomainBlacklist(LocalDateTime.now(), domains);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
