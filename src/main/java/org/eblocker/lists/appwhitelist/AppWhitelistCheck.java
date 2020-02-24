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
package org.eblocker.lists.appwhitelist;

import org.eblocker.server.common.blacklist.DomainFilter;
import org.eblocker.server.common.blacklist.FilterDecision;
import org.eblocker.server.common.blacklist.HashFileFilter;
import org.eblocker.server.common.blacklist.HashingFilter;
import org.eblocker.server.common.blacklist.HostnameFilter;
import org.eblocker.server.common.blacklist.SingleFileFilter;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.lists.easylist.EasyListRuleTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class AppWhitelistCheck {
    private static final Logger log = LoggerFactory.getLogger(AppWhitelistCheck.class);

    private static final String APP_WHITELIST_FILE_NAME = "file:lists/appModules.json";
    private static final String DOMAIN_FILTER_METADATA_FILE_NAME = "file:lists/parentalcontrol-filter.json";
    private static final List<String> EASYLISTS_SOURCE_FILES_NAMES = Arrays.asList("file:lists_src/easylistgermany.txt_src",
            "file:lists_src/easyprivacy.txt_src",
            "file:lists_src/easylist.txt_src");
    private static final String ALLOWED_DOMAINS_FILE_NAME = "classpath:app-whitelist-domains.txt";

    private final ObjectMapper objectMapper = new ObjectMapper();

    AppWhitelistCheck() {
    }

    public static void main(String[] args) throws IOException {
        AppWhitelistCheck check = new AppWhitelistCheck();
        Result result = check.run(
            APP_WHITELIST_FILE_NAME,
            ALLOWED_DOMAINS_FILE_NAME,
            DOMAIN_FILTER_METADATA_FILE_NAME,
            EASYLISTS_SOURCE_FILES_NAMES);
        result.print();
        if (result.getNumberOfAllowedBlockedDomains() < result.getNumberOfBlockedDomains()) {
            log.warn("App whitelist contains not allowed domains!");
            System.exit(1);
        }
    }

    Result run(String appWhitelistFileName, String allowedDomainsFileName, String domainFilterMetadataFileName, List<String> easylistsSourceFilesNames) throws IOException {
        List<AppWhitelistModule> appWhitelists = loadAppWhitelists(appWhitelistFileName);
        log.info("loaded {} app whitelists", appWhitelists.size());

        List<DomainFilter<String>> domainFilters = loadDomainFilters(domainFilterMetadataFileName);
        log.info("loaded {} domain filters", domainFilters.size());

        List<EasylistFilter> easylistFilters = loadEasyListFilters(easylistsSourceFilesNames);
        log.info("loaded {} easylist filters", easylistFilters.size());

        Set<String> allowedDomains = loadAllowedDomains(allowedDomainsFileName);
        log.info("loaded {} allowed domains", allowedDomains.size());

        Map<AppWhitelistModule, List<BlockedDomain>> blockedDomainsByAppWhitelist = new IdentityHashMap<>();
        for (AppWhitelistModule appWhitelist : appWhitelists) {
            log.debug("{}: {} checking {} whitelisted domains", appWhitelist.getId(), appWhitelist.getName(), appWhitelist.getWhitelistedDomains().size());

            List<BlockedDomain> blockedDomains = new ArrayList<>();
            for (String domain : appWhitelist.getWhitelistedDomains()) {
                blockedDomains.addAll(checkDomainFilters(domain, domainFilters, allowedDomains));
                blockedDomains.addAll(checkEasylistFilters(domain, easylistFilters, allowedDomains));
            }

            blockedDomainsByAppWhitelist.put(appWhitelist, blockedDomains);
        }

        return new Result(blockedDomainsByAppWhitelist);
    }

    private List<BlockedDomain> checkDomainFilters(String domain, List<DomainFilter<String>> domainFilters, Set<String> allowedDomains) {
        List<BlockedDomain> blockedDomains = new ArrayList<>();
        for (DomainFilter<String> domainFilter : domainFilters) {
            FilterDecision<String> decision = domainFilter.isBlocked(domain);
            if (decision.isBlocked()) {
                log.debug("  {}: {} blocked by {}: {}", domain, decision.getDomain(), decision.getFilter().getListId(), decision.getFilter().getName());
                blockedDomains.add(new BlockedDomain(domain, "domain filter id " + decision.getFilter().getListId(), decision.getDomain(), allowedDomains.contains(domain)));
            }
        }
        return blockedDomains;
    }

    private List<BlockedDomain> checkEasylistFilters(String domain, List<EasylistFilter> easylistFilters, Set<String> allowedDomains) {
        List<BlockedDomain> blockedDomains = new ArrayList<>();
        for (EasylistFilter easylistFilter : easylistFilters) {
            List<FilterResult> results = easylistFilter.filter(domain);
            for(FilterResult result : results) {
                log.debug("  {}: blocked by {}: {}", domain, easylistFilter.getName(), result.getDecider().getDefinition());
                blockedDomains.add(new BlockedDomain(domain, easylistFilter.getName(), result.getDecider().getDefinition(), allowedDomains.contains(domain)));

            }
        }
        return blockedDomains;
    }

    private Set<String> loadAllowedDomains(String allowedDomainsFileName) {
        return ResourceHandler.readLinesAsSet(new SimpleResource(allowedDomainsFileName));
    }

    private List<DomainFilter<String>> loadDomainFilters(String domainFilterMetadataFileName) throws IOException {
        EnumSet<Category> categories = EnumSet.of(Category.ADS, Category.TRACKERS);
        return loadDomainFilterMetadata(domainFilterMetadataFileName)
            .stream()
            .filter(d -> categories.contains(d.getCategory()))
            .filter(d -> d.getFormat().startsWith("domainblacklist"))
            .filter(d -> "blacklist".equals(d.getFilterType()))
            .map(this::loadDomainFilter)
            .collect(Collectors.toList());
    }

    private List<ParentalControlFilterMetaData> loadDomainFilterMetadata(String domainFilterMetadataFileName) throws IOException {
        try (InputStream in = ResourceHandler.getInputStream(new SimpleResource(domainFilterMetadataFileName))) {
            return objectMapper.readValue(in, new TypeReference<List<ParentalControlFilterMetaData>>() {
            });
        }
    }

    private DomainFilter<String> loadDomainFilter(ParentalControlFilterMetaData metadata) {
        try {
            Path path = Paths.get(metadata.getFilenames().get(0));
            DomainFilter<String> filter;
            switch (metadata.getFormat()) {
                case "domainblacklist":
                case "domainblacklist/string":
                    filter = new SingleFileFilter(StandardCharsets.UTF_8, path);
                    break;
                case "domainblacklist/md5":
                    filter = new HashingFilter(Hashing.md5(), new HashFileFilter(path));
                    break;
                case "domainblacklist/sha1":
                    filter = new HashingFilter(Hashing.sha1(), new HashFileFilter(path));
                    break;
                default:
                    throw new IllegalArgumentException("unknown format " + metadata.getFormat());
            }
            return new HostnameFilter(filter);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<EasylistFilter> loadEasyListFilters(List<String> easylistsSourceFilesNames) {
        return easylistsSourceFilesNames.stream()
                .map(this::loadEasylistFilter)
                .collect(Collectors.toList());
    }

    private EasylistFilter loadEasylistFilter(String name) {
        List<Filter> filters = ResourceHandler.readLines(new SimpleResource(name))
            .stream()
            .map(line -> new EasyListLineParser().parseLine(line))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        return new EasylistFilter(name, filters);
    }

    private List<AppWhitelistModule> loadAppWhitelists(String appWhitelistFileName) throws IOException {
        try (InputStream in = ResourceHandler.getInputStream(new SimpleResource(appWhitelistFileName))) {
            return objectMapper.readValue(in, new TypeReference<List<AppWhitelistModule>>() {
            });
        }
    }

    private static class EasylistFilter {
        private String name;
        private List<Filter> filters;

        EasylistFilter(String name, List<Filter> filters) {
            this.name = name;
            this.filters = filters;
        }

        String getName() {
            return name;
        }

        List<FilterResult> filter(String domain) {
            EasyListRuleTest test = new EasyListRuleTest("https://" + domain + "/");

            List<FilterResult> results = filters.stream()
                    .map(f -> f.filter(test))
                    .collect(Collectors.toList());

            Set<Decision> decisions = results.stream().map(FilterResult::getDecision).collect(Collectors.toSet());
            if (decisions.contains(Decision.PASS) || !decisions.contains(Decision.BLOCK)) {
                return Collections.emptyList();
            }

            return results.stream().filter(f -> f.getDecision() == Decision.BLOCK).collect(Collectors.toList());
        }
    }

    static class BlockedDomain {
        private final String domain;
        private final String blockedBy;
        private final String additionalInfo;
        private final boolean allowed;

        BlockedDomain(String domain, String blockedBy, String additionalInfo, boolean allowed) {
            this.domain = domain;
            this.blockedBy = blockedBy;
            this.additionalInfo = additionalInfo;
            this.allowed = allowed;
        }

        String getDomain() {
            return domain;
        }

        String getBlockedBy() {
            return blockedBy;
        }

        String getAdditionalInfo() {
            return additionalInfo;
        }

        boolean isAllowed() {
            return allowed;
        }
    }

    static class Result {
        private final Map<AppWhitelistModule, List<BlockedDomain>> blockedDomainsByAppWhitelist;

        private Result(
            Map<AppWhitelistModule, List<BlockedDomain>> blockedDomainsByAppWhitelist) {
            this.blockedDomainsByAppWhitelist = blockedDomainsByAppWhitelist;
        }

        Map<Integer, List<BlockedDomain>> getBlockedDomainsByAppWhitelistIds() {
            return blockedDomainsByAppWhitelist.entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey().getId(), Map.Entry::getValue));
        }

        long getNumberOfBlockedDomains() {
            return (int) blockedDomainsByAppWhitelist.values()
                .stream()
                .flatMap(List::stream)
                .map(BlockedDomain::getDomain)
                .distinct()
                .count();
        }

        int getNumberOfAllowedBlockedDomains() {
            return (int) blockedDomainsByAppWhitelist.values()
                .stream()
                .flatMap(List::stream)
                .filter(BlockedDomain::isAllowed)
                .map(BlockedDomain::getDomain)
                .distinct()
                .count();
        }

        void print() {
            log.info("App whitelists with blocked domains:");
            blockedDomainsByAppWhitelist.entrySet()
                .stream()
                .sorted(Comparator.comparing(e -> e.getKey().getId()))
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> {
                    log.info("{} {}:", e.getKey().getId(), e.getKey().getName());
                    for (BlockedDomain blockedDomain : e.getValue()) {
                        String state = blockedDomain.isAllowed() ? " OK " : "FAIL";
                        log.info("  [{}]    {} blocked by {} ({})", state, blockedDomain.getDomain(), blockedDomain.getBlockedBy(), blockedDomain.getAdditionalInfo());
                    }
                });

            log.info("{} of {} blocked domains allowed", getNumberOfAllowedBlockedDomains(), getNumberOfBlockedDomains());
        }
    }

}
