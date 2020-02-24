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

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.lists.easylist.EasyListRuleTest;
import org.eblocker.lists.parentalcontrol.DomainBlacklist;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import org.eblocker.lists.util.DomainSet;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.lists.util.HttpClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads a list of the top 1m domains and filters them through EasyList.
 * This currently takes a few hours, so this provider is not (yet) used by
 * the ParentalControlListCreator.
 *
 * Instead, call main() to create the lists and update them in Git.
 */
public class EasyListDomainProvider implements BlacklistProvider {
    private static final Logger log = LoggerFactory.getLogger(EasyListDomainProvider.class);
    private static final String ZIP_ENTRY_NAME = "top-1m.csv";

    private final HttpClient httpClient;

    public EasyListDomainProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public DomainBlacklist createBlacklist(JsonNode sourceParameters) {
        String url = sourceParameters.get("url").asText();
        JsonNode easyLists = sourceParameters.get("easylists");
        List<String> easyListFilenames = StreamSupport.stream(easyLists.spliterator(), false)
                .map(node -> node.asText())
                .collect(Collectors.toList());

        try (InputStream in = httpClient.download(url, "", "")) {
            log.info("downloading {}", url);
            Set<String> allDomains = getTopOneMillionDomains(url, in);
            List<Filter> easyListFilters = getEasyListFilters(easyListFilenames);
            log.info("got {} filters.", easyListFilters.size());
            AtomicInteger countChecked = new AtomicInteger();
            AtomicInteger countBlocked = new AtomicInteger();
            Set<String> blockedDomains = allDomains.stream()
                    .peek(domain -> showProgress(countChecked, countBlocked, allDomains.size()))
                    .filter(domain -> shouldBlock(domain, easyListFilters))
                    .peek(domain -> countBlocked.incrementAndGet())
                    .collect(Collectors.toSet());

            return new DomainBlacklist(LocalDateTime.now(), blockedDomains);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void showProgress(AtomicInteger countChecked, AtomicInteger countBlocked, int totalSize) {
        int i = countChecked.incrementAndGet();
        if (i % 1000 == 0) {
            log.info("number of domains checked: {} / blocked: {} / total: {}", i, countBlocked.get(), totalSize);
        }
    }

    private boolean shouldBlock(String domain, List<Filter> easyListFilters) {
        EasyListRuleTest test = new EasyListRuleTest("https://" + domain + "/");
        Set<Decision> decisions = easyListFilters.parallelStream()
            .map(f -> f.filter(test).getDecision())
            .collect(Collectors.toSet());

        if (decisions.contains(Decision.BLOCK) && !decisions.contains(Decision.PASS)) {
            return true;
        } else {
            return false;
        }
    }

    private List<Filter> getEasyListFilters(List<String> easyLists) throws IOException {
        log.info("Parsing EasyLists: {}", easyLists);
        return easyLists.stream()
                .flatMap(filename -> linesOfFile(filename))
                .map(line -> new EasyListLineParser().parseLine(line))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static Stream<String> linesOfFile(String filename) {
        SimpleResource resource = new SimpleResource(filename);
        InputStream inputStream = ResourceHandler.getInputStream(resource);
        return new BufferedReader(new InputStreamReader(inputStream)).lines();
    }

    private Set<String> getTopOneMillionDomains(String url, InputStream in) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(in);
        ZipEntry entry = zipIn.getNextEntry();
        if (! entry.getName().equals(ZIP_ENTRY_NAME)) {
            log.error("Expected first ZIP entry '{}' in '{}', got: '{}'", ZIP_ENTRY_NAME, url, entry.getName());
            throw new IOException("Could not extract list of domains from '" + url + "'");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(zipIn));
        return reader.lines()
                .map(line -> line.split(",")[1])
                .collect(Collectors.toSet());
    }

    /**
     * Filtering the 1 million domains from the Umbrella list through the EasyList filters
     * takes about 3 hours on a quad-core machine...
     * @param args
     */
    public static void main(String[] args) {
        HttpClient httpClient = HttpClientFactory.create();
        Stream.of("easylistgermany", "easyprivacy", "easylist")
                .forEach(list -> processList(list, httpClient));
    }

    private static void processList(String listname, HttpClient httpClient) {
        final String sourceFile = String.format("file:lists_src/%s.txt_src", listname);
        final String targetFile = String.format("file:src/main/resources/umbrella-%s.txt", listname);

        ObjectMapper objectMapper = new ObjectMapper();
        EasyListDomainProvider provider = new EasyListDomainProvider(httpClient);
        Map<String, Object> sourceParametersMap = new HashMap<>();
        sourceParametersMap.put("url", "http://s3-us-west-1.amazonaws.com/umbrella-static/top-1m.csv.zip");
        sourceParametersMap.put("easylists", new String[]{ sourceFile });
        JsonNode sourceParameters = objectMapper.valueToTree(sourceParametersMap);
        DomainBlacklist blacklist = provider.createBlacklist(sourceParameters);
        Set<String> domains = blacklist.getList();
        DomainSet set = new DomainSet();
        set.add(domains);
        List<String> domainsList = new ArrayList(set.getDomains());
        Collections.sort(domainsList);
        ResourceHandler.writeLines(new SimpleResource(targetFile), domainsList);
        log.info("wrote {} domains to {}", domainsList.size(), targetFile);
    }
}
