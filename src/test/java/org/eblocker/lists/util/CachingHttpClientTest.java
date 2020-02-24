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

import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class CachingHttpClientTest {

    private static final String URL = "http://random.site/path";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    private byte[] content;
    private HttpClient httpClient;
    private CachingHttpClient cachingHttpClient;

    @Before
    public void setUp() throws IOException {
        content = new byte[16384];
        new Random().nextBytes(content);

        httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.download(URL, USERNAME, PASSWORD)).thenReturn(new ByteArrayInputStream(content));

        cachingHttpClient = new CachingHttpClient(httpClient);
    }

    @Test
    public void testDownload() throws IOException {
        InputStream in = cachingHttpClient.download(URL, USERNAME, PASSWORD);

        byte[] downloadContent = ByteStreams.toByteArray(in);
        Assert.assertArrayEquals(downloadContent, content);
    }

    @Test
    public void testCaching() throws IOException {
        InputStream in = cachingHttpClient.download(URL, USERNAME, PASSWORD);
        InputStream in2 = cachingHttpClient.download(URL, USERNAME, PASSWORD);

        Mockito.verify(httpClient, Mockito.times(1)).download(URL, USERNAME, PASSWORD);
        byte[] downloadContent = ByteStreams.toByteArray(in);
        byte[] downloadContent2 = ByteStreams.toByteArray(in2);
        Assert.assertArrayEquals(content, downloadContent);
        Assert.assertArrayEquals(content, downloadContent2);
    }

}