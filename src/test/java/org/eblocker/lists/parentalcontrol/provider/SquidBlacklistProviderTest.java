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
import org.eblocker.lists.util.HttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SquidBlacklistProviderTest {

    private static final String USERNAME = "unit-test-username";
    private static final String PASSWORD = "unit-test-password";

    private ObjectMapper objectMapper;
    private HttpClient httpClient;
    private SquidBlacklistProvider provider;

    @Before
    public void setUp() throws IOException {
        objectMapper = new ObjectMapper();

        httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.download("https://squidblacklist.org/squid-urlshort.acl", USERNAME, PASSWORD)).thenReturn(ClassLoader.getSystemResourceAsStream("squid-urlshort.acl"));

        Properties properties = new Properties();
        properties.setProperty("squidblacklist.org.username", USERNAME);
        properties.setProperty("squidblacklist.org.password", PASSWORD);

        provider = new SquidBlacklistProvider(httpClient, properties);
    }

    @Test
    public void testCreateFilter() {
        Map<String, Object> sourceParametersMap = new HashMap<>();
        sourceParametersMap.put("urls", new String[]{"https://squidblacklist.org/squid-urlshort.acl"});
        sourceParametersMap.put("key", "name");
        JsonNode sourceParameters = objectMapper.valueToTree(sourceParametersMap);

        DomainBlacklist blacklist = provider.createBlacklist(sourceParameters);

        Assert.assertEquals(LocalDate.of(2017, 1, 15).atStartOfDay(), blacklist.getDate());
        Assert.assertEquals(3, blacklist.getList().size());
        Assert.assertTrue(blacklist.getList().contains(".ax.ag"));
        Assert.assertTrue(blacklist.getList().contains(".sharetabs.com"));
        Assert.assertTrue(blacklist.getList().contains(".zzz.ee"));
    }

}