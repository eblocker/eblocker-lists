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
package org.eblocker.lists.parentalcontrol;

import org.eblocker.server.common.blacklist.BlacklistCompiler;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.util.ByteArrays;
import org.eblocker.lists.util.DomainSet;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DomainFilterProvider implements FilterProvider {
    private static final Logger log = LoggerFactory.getLogger(DomainFilterProvider.class);

    private final BlacklistCompiler blacklistCompiler;
    private final ObjectMapper objectMapper;
    private final Map<String, BlacklistProvider> providersByName;

    public DomainFilterProvider(BlacklistCompiler blacklistCompiler,
                                ObjectMapper objectMapper,
                                Map<String, BlacklistProvider> providersByName) {
        this.blacklistCompiler = blacklistCompiler;
        this.objectMapper = objectMapper;
        this.providersByName = providersByName;
    }

    @Override
    public Filter createFilter(ParentalControlFilterSourceMetaData sourceMetaData, Collection<Filter> filters) {
        log.info("creating filter id: {} name: {}", sourceMetaData.getId(), sourceMetaData.getName().get("en"));

        String contentType = sourceMetaData.getProviderParameters().get("contentType").textValue();
        List<ProviderConfig> providers = getProviderConfig(sourceMetaData.getProviderParameters().get("providers"));

        List<Blacklist<?>> includedBlacklists = providers.stream()
            .filter(c -> !c.isExclude())
            .map(this::createBlacklist)
            .collect(Collectors.toList());

        List<Blacklist<?>> excludedBlacklists = providers.stream()
            .filter(ProviderConfig::isExclude)
            .map(this::createBlacklist)
            .collect(Collectors.toList());

        switch (contentType) {
            case "string":
                return createDomainFilter(sourceMetaData, includedBlacklists, excludedBlacklists);
            case "md5":
                return createHashFilter(sourceMetaData, includedBlacklists, excludedBlacklists, new HashFunction("md5", Hashing.md5()));
            case "sha1":
                return createHashFilter(sourceMetaData, includedBlacklists, excludedBlacklists, new HashFunction("sha1", Hashing.sha1()));
            default:
                throw new IllegalArgumentException("do not know how to create " + contentType + " filter");
        }
    }

    private List<ProviderConfig> getProviderConfig(JsonNode node) {
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, ProviderConfig.class);
            return objectMapper.readValue(objectMapper.treeAsTokens(node), type);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse provider config", e);
        }
    }

    private Filter createDomainFilter(ParentalControlFilterSourceMetaData sourceMetaData,
                                      List<Blacklist<?>> includedBlacklists,
                                      List<Blacklist<?>> excludedBlacklists) {
        log.info("creating domain filter id: {} name: {}", sourceMetaData.getId(), sourceMetaData.getName().get("en"));

        DomainBlacklist includedDomains = includedBlacklists.stream()
            .map(l -> (DomainBlacklist) l)
            .reduce(new DomainBlacklist(LocalDateTime.MIN, Collections.emptySet()), Blacklists::mergeBlacklist);

        DomainBlacklist excludedDomains = excludedBlacklists.stream()
            .map(l -> (DomainBlacklist) l)
            .reduce(new DomainBlacklist(LocalDateTime.MIN, Collections.emptySet()), Blacklists::mergeBlacklist);

        log.debug("{} included domains", includedDomains.getList().size());
        log.debug("{} excluded domains", excludedDomains.getList().size());

        DomainSet domains = new DomainSet();
        domains.add(includedDomains.getList());
        domains.remove(excludedDomains.getList());
        DomainBlacklist blacklist = new DomainBlacklist(max(includedDomains.getDate(), excludedDomains.getDate()), domains.getDomains());

        log.debug("compiling domain filter id: {} name: {} with {} domains", sourceMetaData.getId(), sourceMetaData.getName().get("en"), blacklist.getList().size());
        FilterFileNames names = compile(sourceMetaData.getId(), sourceMetaData.getKey(), blacklist);

        log.debug("compiling domain filter id: {} name: {} creating metadata", sourceMetaData.getId(), sourceMetaData.getName().get("en"));
        ParentalControlFilterMetaData metaData = createMetaData(sourceMetaData, blacklist, names);

        return new Filter(metaData, blacklist);
    }

    private Filter createHashFilter(ParentalControlFilterSourceMetaData sourceMetaData,
                                    List<Blacklist<?>> includedBlacklists,
                                    List<Blacklist<?>> excludedBlacklists,
                                    HashFunction hashFunction) {
        log.info("creating hash filter id: {} name: {}", sourceMetaData.getId(), sourceMetaData.getName().get("en"));

        HashBlacklist includedHashes = includedBlacklists.stream()
            .map(l -> hashBlacklist(l, hashFunction))
            .reduce(new HashBlacklist(LocalDateTime.MIN, Collections.emptySet(), hashFunction.getName()), Blacklists::mergeBlacklist);

        HashBlacklist excludedHashes = excludedBlacklists.stream()
            .map(l -> hashBlacklist(l, hashFunction))
            .reduce(new HashBlacklist(LocalDateTime.MIN, Collections.emptySet(), hashFunction.getName()), Blacklists::mergeBlacklist);

        log.debug("{} included domains", includedHashes.getList().size());
        log.debug("{} excluded domains", excludedHashes.getList().size());

        Set<byte[]> hashes = new TreeSet<>(ByteArrays::compare);
        hashes.addAll(includedHashes.getList());
        hashes.removeAll(excludedHashes.getList());

        HashBlacklist blacklist = new HashBlacklist(max(includedHashes.getDate(), excludedHashes.getDate()), hashes, hashFunction.getName());

        log.debug("compiling hash filter id: {} name: {} with {} domains", sourceMetaData.getId(), sourceMetaData.getName().get("en"), blacklist.getList().size());
        FilterFileNames names = compile(sourceMetaData.getId(), sourceMetaData.getKey(), blacklist);

        log.debug("compiling hash filter id: {} name: {} creating metadata", sourceMetaData.getId(), sourceMetaData.getName().get("en"));
        ParentalControlFilterMetaData metaData = createMetaData(sourceMetaData, blacklist, names);

        return new Filter(metaData, blacklist);
    }

    private HashBlacklist hashBlacklist(Blacklist<?> blacklist, HashFunction hashFunction) {
        if (blacklist instanceof HashBlacklist) {
            HashBlacklist hashBlacklist = (HashBlacklist) blacklist;
            if (!hashBlacklist.getHashFunctionName().equals(hashFunction.getName())) {
                throw new IllegalArgumentException("incompatible hash functions. Expected " + hashFunction.getName() + " but got " + hashBlacklist.getHashFunctionName());
            }
            return hashBlacklist;
        }

        log.info("hashing {} domains", blacklist.getList().size());
        DomainBlacklist domainBlacklist = (DomainBlacklist) blacklist;
        Set<byte[]> hashes = domainBlacklist.getList().stream()
            .map(domain -> hashFunction.getFunction().hashString(domain, Charsets.UTF_8))
            .map(HashCode::asBytes)
            .collect(Collectors.toSet());
        return new HashBlacklist(blacklist.getDate(), hashes, hashFunction.getName());
    }

    private Blacklist<?> createBlacklist(ProviderConfig config) {
        BlacklistProvider provider = getProvider(config.getProvider());
        return provider.createBlacklist(config.getSourceParameters());
    }

    private BlacklistProvider getProvider(String name) {
        BlacklistProvider provider = providersByName.get(name);
        if (provider == null) {
            log.error("no provider found for {}", name);
            throw new IllegalStateException("unknown provider " + name);
        }
        return provider;
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return b.isAfter(a) ? b : a;
    }

    private FilterFileNames compile(int id, String key, DomainBlacklist blacklist) {
        try {
            String fileFilterFileName = "lists/squidblacklist.org/" + key + ".filter";
            String bloomFilterFileName = "lists/squidblacklist.org/" + key + ".bloom";
            blacklistCompiler.compile(id, key, new ArrayList<>(blacklist.getList()), fileFilterFileName, bloomFilterFileName);
            return new FilterFileNames(fileFilterFileName, bloomFilterFileName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FilterFileNames compile(int id, String key, HashBlacklist blacklist) {
        try {
            String fileFilterFileName = "lists/squidblacklist.org/" + key + ".filter";
            String bloomFilterFileName = "lists/squidblacklist.org/" + key + ".bloom";
            blacklistCompiler.compileHashFilter(id, key, blacklist.getHashFunctionName(), new ArrayList<>(blacklist.getList()), fileFilterFileName, bloomFilterFileName);
            return new FilterFileNames(fileFilterFileName, bloomFilterFileName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ParentalControlFilterMetaData createMetaData(ParentalControlFilterSourceMetaData sourceMetaData, Blacklist blacklist, FilterFileNames names) {
        ParentalControlFilterMetaData metaData = new ParentalControlFilterMetaData();
        metaData.setId(sourceMetaData.getId());
        metaData.setBuiltin(true);
        metaData.setDate(Date.from(blacklist.getDate().toInstant(ZoneOffset.UTC)));
        metaData.setDescription(sourceMetaData.getDescription());
        metaData.setCategory(sourceMetaData.getCategory());
        metaData.setFilenames(Arrays.asList(names.fileFilterFileName, names.bloomFilterFileName));
        metaData.setFilterType(sourceMetaData.getFilterType());
        metaData.setFormat(blacklist.getFormat());
        metaData.setName(sourceMetaData.getName());
        metaData.setVersion(blacklist.getDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        metaData.setSize(blacklist.getList().size());
        metaData.setQueryTransformations(sourceMetaData.getQueryTransformations());

        return metaData;
    }

    private class FilterFileNames {
        String fileFilterFileName;
        String bloomFilterFileName;

        public FilterFileNames(String fileFilterFileName, String bloomFilterFileName) {
            this.fileFilterFileName = fileFilterFileName;
            this.bloomFilterFileName = bloomFilterFileName;
        }
    }

    private class HashFunction {
        private String name;
        private com.google.common.hash.HashFunction hashFunction;

        public HashFunction(String name, com.google.common.hash.HashFunction hashFunction) {
            this.name = name;
            this.hashFunction = hashFunction;
        }

        public String getName() {
            return name;
        }

        public com.google.common.hash.HashFunction getFunction() {
            return hashFunction;
        }
    }
}
