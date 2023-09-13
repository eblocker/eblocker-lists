/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.lists.scriptlets;

import org.eblocker.lists.tools.ResourceInputStream;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.lists.util.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads scriptlets in uBlock Origin's format.
 */
public class ScriptletsDownloader {
    private static final Logger log = LoggerFactory.getLogger(ScriptletsDownloader.class);
    private static final String PROPERTIES_FILE = "scriptlets.properties";
    private static final String SCRIPTLETS_URL = "scriptlets.url";
    private static final String SCRIPTLETS_DIRECTORY = "scriptlets.directory";
    private static final String SCRIPTLETS_MIN_NUMBER = "scriptlets.minimum_number";

    private final HttpClient httpClient;
    private final Path scriptletsDirectory;

    public ScriptletsDownloader(HttpClient httpClient, Path scriptletsDirectory) {
        this.httpClient = httpClient;
        this.scriptletsDirectory = scriptletsDirectory;
    }

    /**
     * Downloads the scriptlets file, parses the scriptlets and writes them to individual files
     * @param url
     * @return number of unique scriptlets (excluding aliases)
     * @throws IOException
     */
    public int downloadScriptlets(String url) throws IOException {
        AtomicInteger nScriptlets = new AtomicInteger();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpClient.download(url, null, null)))) {
            new ScriptletsParser().parse(reader.lines()).forEach(scriptlet -> {
                writeScriptlet(scriptlet);
                nScriptlets.getAndIncrement();
            });
        }
        return nScriptlets.get();
    }

    private void writeScriptlet(Scriptlet scriptlet) {
        log.info("Writing scriptlet(s): {}", scriptlet.getNames());
        for (String name: scriptlet.getNames()) {
            Path path = scriptletsDirectory.resolve(name);
            try {
                Files.writeString(path, scriptlet.getCode());
            } catch (IOException e) {
                throw new RuntimeException("Could not write scriptlet code to: " + path, e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.load(ResourceInputStream.get(PROPERTIES_FILE));
        String url = properties.getProperty(SCRIPTLETS_URL);
        String dir = properties.getProperty(SCRIPTLETS_DIRECTORY);
        int minNumber = Integer.parseInt(properties.getProperty(SCRIPTLETS_MIN_NUMBER));
        ScriptletsDownloader downloader = new ScriptletsDownloader(HttpClientFactory.create(), Path.of(dir));
        int number = downloader.downloadScriptlets(url);
        if (number < minNumber) {
            log.error("Found only {} scriptlets. Expected at least {}.", number, minNumber);
            throw new RuntimeException("Not enough scriptlets found");
        }
    }
}
