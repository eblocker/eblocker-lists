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

import com.auth0.jwt.internal.org.apache.commons.lang3.StringUtils;
import org.eblocker.lists.malware.MalwarePatrolDownloader;
import org.eblocker.lists.malware.MalwarePatrolSquidGuardParser;
import org.eblocker.lists.parentalcontrol.provider.MalwarePatrolDomainProvider;
import org.eblocker.lists.util.ProprietaryConfigurationLoader;
import org.eblocker.server.common.blacklist.BlacklistCompiler;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.lists.bpjm.BpjmModulZipReader;
import org.eblocker.lists.parentalcontrol.provider.BpjmListProvider;
import org.eblocker.lists.malware.PhishtankDownloader;
import org.eblocker.lists.parentalcontrol.provider.DisconnectMeProvider;
import org.eblocker.lists.parentalcontrol.provider.FragFinnProvider;
import org.eblocker.lists.parentalcontrol.provider.HostsFileProvider;
import org.eblocker.lists.parentalcontrol.provider.ListProvider;
import org.eblocker.lists.parentalcontrol.provider.LocalListProvider;
import org.eblocker.lists.parentalcontrol.provider.MalwareFileDomainProvider;
import org.eblocker.lists.parentalcontrol.provider.PhishtankDomainProvider;
import org.eblocker.lists.parentalcontrol.provider.SquidBlacklistProvider;
import org.eblocker.lists.parentalcontrol.provider.TarGzProvider;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.lists.util.HttpClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class ParentalControlFilterListCreator {
    private static final Logger log = LoggerFactory.getLogger(ParentalControlFilterListCreator.class);

    private static final String BPJM_PROPERTIES = "bpjm.properties";
    private static final String LISTS_PROPERTIES = "lists.properties";
    private static final String SQUIDBLACKLISTS_PROPERTIES = "squidblacklists.properties";
    private static final String MALWARE_PROPERTIES = "malware.properties";

    private static final String INPUT_META_DATA_FILE = "parentalcontrol-filter-config.json";
    private static final String OUTPUT_META_DATA_FILE = "lists/parentalcontrol-filter.json";
    private static final String MAX_FILTER_SIZE = "list.parentalcontrol.max_filter_size";

    private final String inputMetaDataFile;
    private final String outputMetaDataFile;
    private final ObjectMapper objectMapper;
    private final Map<String, FilterProvider> providersByName;
    private final Properties properties;

    public static void main(String[] args) throws IOException {

        BlacklistCompiler blacklistCompiler = new BlacklistCompiler();
        ObjectMapper objectMapper = new ObjectMapper();
        HttpClient httpClient = HttpClientFactory.create();
        Properties malwareProperties = loadProperties(MALWARE_PROPERTIES);

        // MalwarePatrol is not included by default
        BlacklistProvider malwarePatrolProvider;
        if (ProprietaryConfigurationLoader.addProprietaryConfiguration(malwareProperties)) {
            malwarePatrolProvider = new MalwarePatrolDomainProvider(new MalwarePatrolDownloader(httpClient, new MalwarePatrolSquidGuardParser(), malwareProperties));
        } else {
            malwarePatrolProvider = new EmptyListProvider();
        }

        Map<String, BlacklistProvider> blacklistProvidersByName = new HashMap<>();
        blacklistProvidersByName.put("fragFinn", new FragFinnProvider(httpClient));
        blacklistProvidersByName.put("hosts", new HostsFileProvider(httpClient));
        blacklistProvidersByName.put("list", new ListProvider(httpClient));
        blacklistProvidersByName.put("squidblacklist.org", new SquidBlacklistProvider(httpClient, loadProperties(SQUIDBLACKLISTS_PROPERTIES)));
        blacklistProvidersByName.put("targz", new TarGzProvider(httpClient));
        blacklistProvidersByName.put("disconnect.me", new DisconnectMeProvider(httpClient));
        blacklistProvidersByName.put("localList", new LocalListProvider());
        blacklistProvidersByName.put("bpjm", new BpjmListProvider(loadProperties(BPJM_PROPERTIES), new BpjmModulZipReader(), httpClient));
        blacklistProvidersByName.put("malwarepatrol", malwarePatrolProvider);
        blacklistProvidersByName.put("phishtank", new PhishtankDomainProvider(new PhishtankDownloader(malwareProperties, objectMapper, httpClient)));
        blacklistProvidersByName.put("malwareFile", new MalwareFileDomainProvider(objectMapper));

        // Filters are created in order enabling later providers to access to previously created filters
        Map<String, FilterProvider> filterProvidersByName = new LinkedHashMap<>();
        filterProvidersByName.put("domain", new DomainFilterProvider(blacklistCompiler,objectMapper, blacklistProvidersByName));
        filterProvidersByName.put("bloom", new BloomFilterProvider(blacklistCompiler, objectMapper));

        new ParentalControlFilterListCreator(INPUT_META_DATA_FILE, OUTPUT_META_DATA_FILE, objectMapper, filterProvidersByName).run();
    }

    ParentalControlFilterListCreator(String inputMetaDataFile,
                                     String outputMetaDataFile,
                                     ObjectMapper objectMapper,
                                     Map<String, FilterProvider> providersByName) throws IOException {
        this.inputMetaDataFile = inputMetaDataFile;
        this.outputMetaDataFile = outputMetaDataFile;
        this.objectMapper = objectMapper;
        this.providersByName = providersByName;

        properties = loadProperties(LISTS_PROPERTIES);
    }

    void run() throws IOException {
        List<ParentalControlFilterSourceMetaData> sourceMetaData = readSourceMetaData();
        log.info("Creating {} parental control filter lists", sourceMetaData.size());

        Map<String, List<ParentalControlFilterSourceMetaData>> metaDataByProvider = sourceMetaData.stream()
            .collect(Collectors.groupingBy(ParentalControlFilterSourceMetaData::getProvider));

        List<String> unknownProviders = metaDataByProvider.keySet().stream().filter(name -> !providersByName.containsKey(name)).collect(Collectors.toList());
        if (!unknownProviders.isEmpty()) {
            throw new IllegalArgumentException("unknown providers: " + Joiner.on(",").join(unknownProviders));
        }

        List<Filter> filters = new ArrayList<>();
        for(Map.Entry<String, FilterProvider> entry : providersByName.entrySet()) {
            for(ParentalControlFilterSourceMetaData sourceMetadata : metaDataByProvider.get(entry.getKey())) {
                Filter filter = entry.getValue().createFilter(sourceMetadata, ImmutableList.copyOf(filters));
                validate(filter.getMetaData());
                filters.add(filter);
            }
        }
        saveMetaDataFile(filters);

        log.info("Finished");
    }

    private void saveMetaDataFile(List<Filter> filters) throws IOException {
        log.info("saving meta data file for {} filters", filters.size());
        List<ParentalControlFilterMetaData> metaData = filters.stream().map(Filter::getMetaData).collect(Collectors.toList());
        objectMapper.writeValue(new File(outputMetaDataFile), metaData);
        validateIds(metaData);
    }

    private void validate(ParentalControlFilterMetaData metaData) {
        if (metaData.getId() == null) {
            throw new InvalidMetaDataException("id is null");
        }

        if (StringUtils.isBlank(metaData.getFormat())) {
            throw new InvalidMetaDataException("no format specified");
        }

        if (StringUtils.isBlank(metaData.getFilterType())) {
            throw new InvalidMetaDataException("no filter type specified");
        }

        if (metaData.getName() == null || StringUtils.isBlank(metaData.getName().get("en")) || StringUtils.isBlank(metaData.getName().get("de"))) {
            throw new InvalidMetaDataException("name is incomplete");
        }

        if (metaData.getDescription() == null || StringUtils.isBlank(metaData.getDescription().get("en")) || StringUtils.isBlank(metaData.getDescription().get("de"))) {
            throw new InvalidMetaDataException("description is incomplete");
        }

        if (metaData.getFilenames() == null || metaData.getFilenames().isEmpty()) {
            throw new InvalidMetaDataException("no files specified");
        }

        if (StringUtils.isBlank(metaData.getVersion())) {
            throw new InvalidMetaDataException("no version specified");
        }

        Integer maxFilter = Integer.parseInt(properties.getProperty(MAX_FILTER_SIZE));
        if (metaData.getSize() > maxFilter) {
            throw new InvalidMetaDataException(String.format("Filter limit reached. (filter size %d > max size %d)", metaData.getSize(), maxFilter));
        }
    }

    private void validateIds(List<ParentalControlFilterMetaData> metaData) {
        long uniqueIdcount = metaData.stream().map(ParentalControlFilterMetaData::getId).distinct().count();
        if (uniqueIdcount != metaData.size()) {
            throw new InvalidMetaDataException("duplicate ids");
        }
    }

    private List<ParentalControlFilterSourceMetaData> readSourceMetaData() throws IOException {
        return objectMapper.readValue(ClassLoader.getSystemResourceAsStream(inputMetaDataFile), new TypeReference<List<ParentalControlFilterSourceMetaData>>() {
        });
    }

    private static Properties loadProperties(String resource) throws IOException {
        Properties properties = new Properties();
        properties.load(ClassLoader.getSystemResourceAsStream(resource));
        return properties;
    }
}
