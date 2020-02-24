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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DisconnectMeProviderTest {
    private ObjectMapper objectMapper;
    private HttpClient httpClient;
    private DisconnectMeProvider provider;

    @Before
    public void setUp() throws IOException {
        objectMapper = new ObjectMapper();

        httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.download("disconnect.me.json", "", "")).thenReturn(ClassLoader.getSystemResourceAsStream("disconnect.me-services.json"));
        provider = new DisconnectMeProvider(httpClient);
    }

    @Test
    public void createFilter() {
        Map<String, Object> sourceParametersMap = new HashMap<>();
        sourceParametersMap.put("url", "disconnect.me.json");
        sourceParametersMap.put("categories", new String[]{"Advertising", "Analytics"});
        JsonNode sourceParameters = objectMapper.valueToTree(sourceParametersMap);
        DomainBlacklist blacklist = provider.createBlacklist(sourceParameters);
        Set<String> domains = blacklist.getList();
        Assert.assertEquals(7, domains.size());
        Assert.assertTrue(domains.contains("adless.io"));
        Assert.assertTrue(domains.contains("oewa.at"));
        Assert.assertFalse(domains.contains("aol.com")); // Category "Content"
    }
}
