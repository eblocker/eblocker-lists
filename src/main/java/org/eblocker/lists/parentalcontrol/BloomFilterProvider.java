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
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BloomFilterProvider implements FilterProvider {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterProvider.class);

    private final BlacklistCompiler blacklistCompiler;
    private final ObjectMapper objectMapper;

    public BloomFilterProvider(BlacklistCompiler blacklistCompiler, ObjectMapper objectMapper) {
        this.blacklistCompiler = blacklistCompiler;
        this.objectMapper = objectMapper;
    }

    @Override
    public Filter createFilter(ParentalControlFilterSourceMetaData sourceMetaData, Collection<Filter> filters) {
        Set<Category> categories = readCategories(sourceMetaData.getProviderParameters().get("categories"));

        List<Filter> sourceFilters = filters.stream()
            .filter(filter -> categories.contains(filter.getMetaData().getCategory()))
            .filter(filter -> filter.getBlacklist() instanceof DomainBlacklist)
            .collect(Collectors.toList());

        String sourceFilterIds = sourceFilters.stream()
            .map(Filter::getMetaData)
            .map(ParentalControlFilterMetaData::getId)
            .map(Object::toString)
            .collect(Collectors.joining(","));

        log.info("creating top level bloom filter {}: {} for {} blacklists ({})", sourceMetaData.getId(), sourceMetaData.getKey(), sourceFilters.size(), sourceFilterIds);
        DomainBlacklist blacklist = sourceFilters.stream()
            .map(filter -> (DomainBlacklist) filter.getBlacklist())
            .reduce(new DomainBlacklist(LocalDateTime.MIN, Collections.emptySet()), Blacklists::mergeBlacklist);

        log.info("creating bloom filter {} for {} domains", sourceMetaData.getId(), blacklist.getList().size());
        String fileName = compile(sourceMetaData.getId(), sourceMetaData.getKey(), blacklist);

        ParentalControlFilterMetaData metaData = new ParentalControlFilterMetaData();
        metaData.setId(sourceMetaData.getId());
        metaData.setBuiltin(true);
        metaData.setCategory(sourceMetaData.getCategory());
        metaData.setDate(Date.from(blacklist.getDate().toInstant(ZoneOffset.UTC)));
        metaData.setFilenames(Collections.singletonList(fileName));
        metaData.setFilterType("bloom");
        metaData.setFormat("domainblacklist/bloom");
        metaData.setSize(blacklist.getList().size());
        metaData.setVersion(blacklist.getDate().format(DateTimeFormatter.BASIC_ISO_DATE));

        Map<String, String> names = new HashMap<>();
        names.put("en", sourceMetaData.getKey());
        names.put("de", sourceMetaData.getKey());
        metaData.setName(names);
        metaData.setDescription(names);

        return new Filter(metaData, blacklist);
    }

    private Set<Category> readCategories(JsonNode node) {
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(Set.class, Category.class);
            return objectMapper.readValue(objectMapper.treeAsTokens(node), type);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse provider config", e);
        }
    }

    private String compile(int id, String key, Blacklist<String> blacklist) {
        try {
            String bloomFile = "lists/squidblacklist.org/" + key + ".bloom";
            Path filterFilePath = Files.createTempFile("bloom-" + id + "-" + key, ".filter");
            blacklistCompiler.compile(id, key, new ArrayList<>(blacklist.getList()), filterFilePath.toString(), bloomFile);
            Files.delete(filterFilePath);
            return bloomFile;
        } catch (IOException e) {
            throw new UncheckedIOException("compiling bloom filter failed", e);
        }
    }
}
