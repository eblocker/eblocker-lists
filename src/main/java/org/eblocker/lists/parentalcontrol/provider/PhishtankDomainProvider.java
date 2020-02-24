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

import org.eblocker.server.common.malware.MalwareUtils;
import org.eblocker.server.common.util.IpUtils;
import org.eblocker.lists.malware.MalwareListException;
import org.eblocker.lists.malware.PhishtankDownloader;
import org.eblocker.lists.malware.PhishtankEntry;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import org.eblocker.lists.parentalcontrol.DomainBlacklist;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PhishtankDomainProvider implements BlacklistProvider {
    private final static Logger log = LoggerFactory.getLogger(PhishtankDomainProvider.class);

    private final PhishtankDownloader downloader;

    public PhishtankDomainProvider(PhishtankDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public DomainBlacklist createBlacklist(JsonNode sourceParameters) {
        try {
            List<PhishtankEntry> entries = downloader.retrieveEntries();
            Set<String> domains = entries.stream()
                .map(PhishtankEntry::getUrl)
                .map(MalwareUtils::normalize)
                .filter(e -> !e.contains("/"))
                .map(MalwareUtils::stripPort)
                .filter(e -> !IpUtils.isIPAddress(e))
                .collect(Collectors.toSet());
            log.info("extracted {} domains out of {} entries", domains.size(), entries.size());
            return new DomainBlacklist(LocalDateTime.now(), domains);
        } catch (IOException | MalwareListException e) {
            throw new RuntimeException("something went wrong", e);
        }
    }
}
