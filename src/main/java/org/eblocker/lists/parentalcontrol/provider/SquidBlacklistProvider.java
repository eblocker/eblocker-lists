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

import org.eblocker.lists.parentalcontrol.DomainBlacklist;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import org.eblocker.lists.util.HttpClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SquidBlacklistProvider implements BlacklistProvider {

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d+-\\d+-\\d{4})");
    private static final String USERNAME_KEY = "squidblacklist.org.username";
    private static final String PASSWORD_KEY = "squidblacklist.org.password"; //NOSONAR: this is no password ...

    private final HttpClient httpClient;
    private final String username;
    private final String password;

    public SquidBlacklistProvider(HttpClient httpClient, Properties properties) throws IOException {
        this.httpClient = httpClient;
        this.username = properties.getProperty(USERNAME_KEY);
        this.password = properties.getProperty(PASSWORD_KEY);
    }

    @Override
    public DomainBlacklist createBlacklist(JsonNode sourceParameters) {
        try {
            JsonNode urls = sourceParameters.get("urls");
            LocalDateTime date = LocalDateTime.MIN;
            Set<String> domains = new HashSet<>();
            for(JsonNode url : urls) {
                InputStream in = httpClient.download(url.asText(), username, password);
                DomainBlacklist blacklist = parse(in);
                if (blacklist.getDate().isAfter(date)) {
                    date = blacklist.getDate();
                }
                domains.addAll(blacklist.getList());
            }

            return new DomainBlacklist(date, domains);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static DomainBlacklist parse(InputStream inputStream) throws IOException {
        LocalDateTime date = LocalDateTime.now();
        Set<String> domains = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("#")) {
                    Matcher matcher = DATE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        date = LocalDate.parse(matcher.group(1), DateTimeFormatter.ofPattern("MM-dd-uuuu")).atStartOfDay();
                    }
                } else {
                    domains.add(line);
                }
            }
        }

        return new DomainBlacklist(date, domains);
    }
}
