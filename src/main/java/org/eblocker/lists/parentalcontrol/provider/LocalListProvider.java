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

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.lists.parentalcontrol.Blacklist;
import org.eblocker.lists.parentalcontrol.DomainBlacklist;
import org.eblocker.lists.parentalcontrol.HashBlacklist;
import org.eblocker.lists.parentalcontrol.InvalidMetaDataException;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalListProvider implements BlacklistProvider {
    private static Logger log = LoggerFactory.getLogger(LocalListProvider.class);

    @Override
    public Blacklist createBlacklist(JsonNode sourceParameters) {
        String resourceName = sourceParameters.get("resource").asText();
        JsonNode formatNode = sourceParameters.get("format");
        String format = formatNode != null ? formatNode.asText() : "string";

        BufferedReader reader = resourceReader(resourceName);

        Set<String> domains = reader.lines()
                .map(String::trim)
                .filter(line -> ! line.isEmpty())
                .filter(line -> ! line.startsWith("#"))
                .collect(Collectors.toSet());

        log.info("read {} domains from local resource {}", domains.size(), resourceName);

        if (format.equals("sha1")) {
            return hashBlackList(LocalDateTime.now(), domains);
        } else if (format.equals("string")) {
            return new DomainBlacklist(LocalDateTime.now(), domains);
        } else {
            throw new InvalidMetaDataException("Invalid format: " + format);
        }
    }

    private Blacklist hashBlackList(LocalDateTime date, Set<String> domains) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            Set<byte[]> hashes = domains.stream()
                .map(domain -> {
                    digest.reset();
                    digest.update(domain.getBytes());
                    return digest.digest();
                })
                .collect(Collectors.toSet());
            return new HashBlacklist(date, hashes, "sha1");

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not create blacklist with SHA-1 hashes", e);
        }
    }

    private static BufferedReader resourceReader(String resourceName) {
        SimpleResource resource = new SimpleResource(resourceName);
        InputStream inputStream = ResourceHandler.getInputStream(resource);
        return new BufferedReader(new InputStreamReader(inputStream));
    }
}
