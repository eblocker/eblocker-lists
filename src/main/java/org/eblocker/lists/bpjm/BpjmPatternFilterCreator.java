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
package org.eblocker.lists.bpjm;

import org.eblocker.lists.util.Digests;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.lists.util.HttpClientFactory;
import org.eblocker.server.icap.filter.bpjm.BpjmEntry;
import org.eblocker.server.icap.filter.bpjm.BpjmModul;
import org.eblocker.server.icap.filter.bpjm.BpjmModulSerializer;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class BpjmPatternFilterCreator {

    private static final Logger log = LoggerFactory.getLogger(BpjmPatternFilterCreator.class);

    private static final byte[] EMPTY_STRING_MD5 = Digests.md5("");

    private final Properties properties;
    private final BpjmModulSerializer serializer;
    private final BpjmModulZipReader zipReader;
    private final HttpClient httpClient;

    public static void main(String[] args) throws IOException {
        Properties properties = new Properties();
        properties.load(ClassLoader.getSystemResourceAsStream("bpjm.properties"));
        BpjmModulZipReader zipReader = new BpjmModulZipReader();
        BpjmModulSerializer serializer = new BpjmModulSerializer();
        HttpClient httpClient = HttpClientFactory.create();
        new BpjmPatternFilterCreator(properties, serializer, zipReader, httpClient).run();
    }

    BpjmPatternFilterCreator(Properties properties,
                             BpjmModulSerializer serializer,
                             BpjmModulZipReader zipReader,
                             HttpClient httpClient) {
        this.properties = properties;
        this.serializer = serializer;
        this.zipReader = zipReader;
        this.httpClient = httpClient;
    }

    void run() throws IOException {
        InputStream in;
        String url = properties.getProperty("bpjm.url");
        if (url != null) {
            log.info("downloading {}", url);
            in = httpClient.download(url, "", "");
        } else {
            String path = properties.getProperty("bpjm.path");
            if (path != null) {
                SimpleResource resource = new SimpleResource(path);
                in = ResourceHandler.getInputStream(resource);
            } else {
                throw new IOException("bpfm properties needs either a url or path");
            }
        }

        BpjmModul bpjmModul = zipReader.read(in);
        log.info("parsed {} bpjm entries", bpjmModul.getEntries().size());

        BpjmModul bpjmModulPathEntries = removeDomainEntries(bpjmModul);
        log.info("{} bpjm entries with paths", bpjmModulPathEntries.getEntries().size());

        String outputFile = properties.getProperty("bpjm.out");
        try (OutputStream out = Files.newOutputStream(Paths.get(outputFile))) {
            serializer.write(bpjmModulPathEntries, out);
        }
    }

    private BpjmModul removeDomainEntries(BpjmModul in) {
        List<BpjmEntry> entriesWithPath = in.getEntries().stream()
            .filter(e -> e.getDepth() != 0 || !Arrays.equals(e.getPathHash(), EMPTY_STRING_MD5))
            .collect(Collectors.toList());
        return new BpjmModul(entriesWithPath, in.getLastModified());
    }
}
