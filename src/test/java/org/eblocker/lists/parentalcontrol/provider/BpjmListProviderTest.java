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

import org.eblocker.lists.bpjm.BpjmModulZipReader;
import org.eblocker.lists.parentalcontrol.HashBlacklist;
import org.eblocker.lists.util.Digests;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.server.icap.filter.bpjm.BpjmEntry;
import org.eblocker.server.icap.filter.bpjm.BpjmModul;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Properties;

public class BpjmListProviderTest {

    private static final String URL = "https://example.com/BPjM-Modul.zip";

    private Properties properties;
    private BpjmModulZipReader zipReader;
    private HttpClient httpClient;
    private LocalDateTime localDateTime;

    @Before
    public void setup() throws IOException {
        properties = new Properties();
        properties.setProperty("bpjm.url", URL);
        properties.setProperty("bpjm.hashFunctionName", "md5");
        zipReader = Mockito.mock(BpjmModulZipReader.class);
        httpClient = Mockito.mock(HttpClient.class);

        localDateTime = LocalDateTime.of(2018, 11, 7, 15, 30, 0);
        BpjmModul bpjmModul = new BpjmModul(
                Arrays.asList(
                        new BpjmEntry(Digests.md5("http://eblocker.com"), Digests.md5(""), 0),
                        new BpjmEntry(Digests.md5("http://pouet.net"), Digests.md5(""), 0),
                        new BpjmEntry(Digests.md5("http://etracker.com"), Digests.md5("index.html"), 0),
                        new BpjmEntry(Digests.md5("http://etracker.com"), Digests.md5("hello/world"), 1)),
                localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        InputStream zipStream = Mockito.mock(InputStream.class);
        Mockito.when(httpClient.download(URL, "", "")).thenReturn(zipStream);
        Mockito.when(zipReader.read(zipStream)).thenReturn(bpjmModul);
    }

    @Test
    public void testProvider() {
        BpjmListProvider provider = new BpjmListProvider(properties, zipReader, httpClient);
        HashBlacklist blacklist = provider.createBlacklist(null);
        Assert.assertEquals(localDateTime, blacklist.getDate());
        Assert.assertEquals(2, blacklist.getList().size());
        Assert.assertEquals("md5", blacklist.getHashFunctionName());
        Assert.assertTrue(blacklist.getList().contains(Digests.md5("http://eblocker.com")));
        Assert.assertTrue(blacklist.getList().contains(Digests.md5("http://pouet.net")));
    }
}