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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class OnDiskCachingHttpClient implements HttpClient {

    private final String cachePath;
    private final HttpClient httpClient;

    public OnDiskCachingHttpClient(String cachePath, HttpClient httpClient) {
        this.cachePath = cachePath;
        this.httpClient = httpClient;
    }

    @Override
    public InputStream download(String url, String username, String password) {
        try {
            InputStream entry = getEntry(url);
            if (entry != null) {
                return entry;
            }

            try (InputStream in = httpClient.download(url, username, password)) {
                writeEntry(url, in);
                return getEntry(url);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream getEntry(String url) throws IOException {
        Path path = getPath(url);
        try {
            return new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ));
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    private void writeEntry(String url, InputStream in) throws IOException {
        Path path = getPath(url);
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteStreams.copy(in, out);
        }
    }

    private Path getPath(String url) throws UnsupportedEncodingException {
        String fileName = URLEncoder.encode(url, "UTF-8");
        return Paths.get(cachePath, fileName);
    }
}
