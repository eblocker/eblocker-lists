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

import org.eblocker.lists.parentalcontrol.HashBlacklist;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.server.common.util.ByteArrays;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

public class FragFinnProviderTest {

    @Test
    public void createBlacklist() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        HttpClient httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.download("http://api.fragfinn.de/latest.xml", "", "")).thenReturn(ClassLoader.getSystemResourceAsStream("fragfinn.xml"));

        FragFinnProvider provider = new FragFinnProvider(httpClient);
        HashBlacklist blacklist = provider.createBlacklist(objectMapper.valueToTree(Collections.singletonMap("url", "http://api.fragfinn.de/latest.xml")));

        Assert.assertTrue(Duration.between(LocalDateTime.now(), blacklist.getDate()).toMillis() < 10000);
        Assert.assertEquals(4, blacklist.getList().size());
        Assert.assertTrue(contains(blacklist.getList(), sha1(".google.com")));
        Assert.assertTrue(contains(blacklist.getList(), sha1(".youtube.com")));
        Assert.assertTrue(contains(blacklist.getList(), sha1(".facebook.com")));
        Assert.assertTrue(contains(blacklist.getList(), sha1(".baidu.com")));
    }

    private boolean contains(Collection<byte[]> collection, byte[] value) {
        return collection.stream().anyMatch(m -> ByteArrays.compare(m, value) == 0);
    }

    private byte[] sha1(String value) {
        return Hashing.sha1().hashString(value, Charsets.UTF_8).asBytes();
    }
}