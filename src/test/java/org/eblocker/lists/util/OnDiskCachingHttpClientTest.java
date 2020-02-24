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
package org.eblocker.lists.util;

import org.eblocker.server.common.util.FileUtils;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OnDiskCachingHttpClientTest {

    private static final String USERNAME = "joe";
    private static final String PASSWORD = "123456";

    private Path cachePath;
    private HttpClient httpClient;
    private Map<String, byte[]> content;
    private OnDiskCachingHttpClient cachingHttpClient;

    @Before
    public void setUp() throws IOException {
        cachePath = Files.createTempDirectory("on-disk-cache-test");

        httpClient = Mockito.mock(HttpClient.class);

        content = new HashMap<>();
        content.put("http://1.2.3.4/test?id=abc&arg=5", createRandomByteArray(16384));
        content.put("https://random.host/test/?id=1;pos=2", createRandomByteArray(32768));
        content.put("https://eblocker.com/test.cgi", createRandomByteArray(65536));
        for(Map.Entry<String, byte[]> e : content.entrySet()) {
            Mockito.when(httpClient.download(e.getKey(), USERNAME, PASSWORD)).thenReturn(new ByteArrayInputStream(e.getValue()));
        }

        cachingHttpClient = new OnDiskCachingHttpClient(cachePath.toString(), httpClient);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(cachePath);
    }

    @Test
    public void testPopulatingCache() throws IOException {
        // first round, must populate cache
        for(Map.Entry<String, byte[]> e : content.entrySet()) {
            InputStream in = cachingHttpClient.download(e.getKey(), USERNAME, PASSWORD);
            byte[] downloadedContent = ByteStreams.toByteArray(in);
            Assert.assertArrayEquals(e.getValue(), downloadedContent);
        }
        Assert.assertEquals(3, Files.list(cachePath).count());

        // second round, must not access http client
        for(Map.Entry<String, byte[]> e : content.entrySet()) {
            InputStream in = cachingHttpClient.download(e.getKey(), USERNAME, PASSWORD);
            byte[] downloadedContent = ByteStreams.toByteArray(in);
            Assert.assertArrayEquals(e.getValue(), downloadedContent);
        }
        Mockito.verify(httpClient, Mockito.times(3)).download(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testPopulatedCache() throws IOException {
        // populate cache
        for(Map.Entry<String, byte[]> e : content.entrySet()) {
            Files.write(Paths.get(cachePath.toString(), URLEncoder.encode(e.getKey(), "UTF-8")), e.getValue());
        }

        // download all urls, must issue no remote calls at all
        for(Map.Entry<String, byte[]> e : content.entrySet()) {
            InputStream in = cachingHttpClient.download(e.getKey(), USERNAME, PASSWORD);
            byte[] downloadedContent = ByteStreams.toByteArray(in);
            Assert.assertArrayEquals(e.getValue(), downloadedContent);
        }
        Mockito.verifyZeroInteractions(httpClient);
    }

    byte[] createRandomByteArray(int size) {
        byte[] bytes = new byte[size];
        Random random = new Random();
        random.nextBytes(bytes);
        return bytes;
    }
}