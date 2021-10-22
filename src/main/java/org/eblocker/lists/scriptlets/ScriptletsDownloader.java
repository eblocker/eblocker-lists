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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Downloads scriptlets in uBlock Origin's format.
 */
public class ScriptletsDownloader {
    private static final String PROPERTIES_FILE = "scriptlets.properties";
    private static final String SCRIPTLETS_URL = "scriptlets.url";
    private static final String SCRIPTLETS_DIRECTORY = "scriptlets.directory";

    private final HttpClient httpClient;
    private final Path scriptletsDirectory;

    public ScriptletsDownloader(HttpClient httpClient, Path scriptletsDirectory) {
        this.httpClient = httpClient;
        this.scriptletsDirectory = scriptletsDirectory;
    }

    public void downloadScriptlets(String url) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpClient.download(url, null, null)))) {
            new ScriptletsParser().parse(reader.lines())
                .forEach(this::writeScriptlet);
        }
    }

    private void writeScriptlet(Scriptlet scriptlet) {
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
        ScriptletsDownloader downloader = new ScriptletsDownloader(HttpClientFactory.create(), Path.of(dir));
        downloader.downloadScriptlets(url);
    }
}
