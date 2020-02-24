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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TarGzProviderTest {

    private static final String TAR_GZ_RESOURCE = "test-targz-provider.tar.gz";
    private static final String TAR_GZ_DOMAINS_PATH = "test/category-c/domains";
    private static final String URL  = "http://targz.de/test.tar.gz";

    private HttpClient client;
    private ObjectMapper objectMapper;

    @Before
    public void setup() throws IOException {
        client = Mockito.mock(HttpClient.class);
        Mockito.when(client.download(URL, "", "")).thenReturn(ClassLoader.getSystemResourceAsStream(TAR_GZ_RESOURCE));
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testProvider() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("url", URL);
        parameters.put("path", TAR_GZ_DOMAINS_PATH);

        TarGzProvider provider = new TarGzProvider(client);
        DomainBlacklist blacklist = provider.createBlacklist(objectMapper.valueToTree(parameters));

        Assert.assertEquals(LocalDateTime.of(2018, 2, 28, 10, 33, 41), blacklist.getDate());
        Assert.assertEquals(Sets.newHashSet(".test3.com", ".test4.com"), blacklist.getList());
    }

}