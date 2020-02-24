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
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.NottableString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

public class DefaultHttpClientTest {

    private byte[] existingResource = new byte[4096];

    private ClientAndServer mockServer;
    private String baseUrl;
    private HttpClient httpClient;

    @Before
    public void setUpMockServer() throws IOException {
        Random random = new Random();
        random.nextBytes(existingResource);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(baos)) {
            out.write(existingResource);
        }
        byte[] existingResourceGzipped = baos.toByteArray();

        mockServer = ClientAndServer.startClientAndServer(ThreadLocalRandom.current().nextInt(8000, 9000));
        mockServer.when(HttpRequest.request()
            .withMethod("GET")
            .withPath("/auth/squid-urlshort.acl")
            .withHeader("Accept", "*/*")
            .withHeader("Authorization", "Basic dW5pdC10ZXN0LXVzZXJuYW1lOnVuaXQtdGVzdC1wYXNzd29yZA=="), Times.exactly(1))
            .respond(HttpResponse.response()
                .withStatusCode(200)
                .withBody(existingResource));

        mockServer.when(HttpRequest.request()
            .withMethod("GET")
            .withPath("/noauth/squid-urlshort.acl")
            .withHeader("Accept", "*/*")
            .withHeader(NottableString.not("Authorization"), NottableString.string(".*")))
            .respond(HttpResponse.response()
                .withStatusCode(200)
                .withBody(existingResource));

        mockServer.when(HttpRequest.request()
            .withMethod("GET")
            .withPath("/gzip/squid-urlshort.acl")
            .withHeader("Accept", "*/*")
            .withHeader("Accept-Encoding", "gzip"))
            .respond(HttpResponse.response()
                .withStatusCode(200)
                .withHeader("Content-Encoding", "gzip")
                .withBody(existingResourceGzipped));

        mockServer.when(HttpRequest.request()
                .withMethod("GET")
                .withPath("/unauthorized"))
                .respond(HttpResponse.response()
                    .withStatusCode(401));

        mockServer.when(HttpRequest.request()
                .withMethod("GET")
                .withPath("/not-existing.acl"))
                .respond(HttpResponse.response()
                    .withStatusCode(404));

        baseUrl = "http://127.0.0.1:" + mockServer.getPort();

        httpClient = new DefaultHttpClient();
    }

    @After
    public void tearDownMockServer() {
        mockServer.stop();
    }

    @Test
    public void testDownloadWithAuth() throws IOException {
        InputStream in = httpClient.download(baseUrl + "/auth/squid-urlshort.acl", "unit-test-username", "unit-test-password");
        Assert.assertArrayEquals(existingResource, ByteStreams.toByteArray(in));
    }

    @Test
    public void testDownloadNoAuth() throws IOException {
        InputStream in = httpClient.download(baseUrl + "/noauth/squid-urlshort.acl", "", "");
        Assert.assertArrayEquals(existingResource, ByteStreams.toByteArray(in));
    }

    @Test
    public void testDownloadGzipEncoded() throws IOException {
        InputStream in = httpClient.download(baseUrl + "/gzip/squid-urlshort.acl", "", "");
        Assert.assertArrayEquals(existingResource, ByteStreams.toByteArray(in));
    }

    @Test(expected = IOException.class)
    public void testAuthFailed() throws IOException {
        httpClient.download(baseUrl + "/unauthorized", "foo", "bar");
    }

    @Test(expected = IOException.class)
    public void testNotFound() throws IOException {
        httpClient.download(baseUrl + "/not-existing.acl", "unit-test-username", "unit-test-password");
    }
}