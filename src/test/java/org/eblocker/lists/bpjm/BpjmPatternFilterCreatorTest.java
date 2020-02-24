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
package org.eblocker.lists.bpjm;

import org.eblocker.server.icap.filter.bpjm.BpjmEntry;
import org.eblocker.server.icap.filter.bpjm.BpjmModul;
import org.eblocker.server.icap.filter.bpjm.BpjmModulSerializer;
import org.eblocker.lists.util.Digests;
import org.eblocker.lists.util.HttpClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class BpjmPatternFilterCreatorTest {

    private static final String URL = "https://eblocker.com/bpjm.zip";
    private static final long LAST_MODIFIED = System.currentTimeMillis();
    private Path outPath;

    private BpjmModulSerializer serializer;
    private BpjmPatternFilterCreator creator;

    @Before
    public void setUp() throws IOException {
        outPath = Files.createTempFile(this.getClass().toString(), null);
        Files.delete(outPath);

        Properties properties = new Properties();
        properties.setProperty("bpjm.url", URL);
        properties.setProperty("bpjm.out", outPath.toString());
        properties.setProperty("bpjm.hashFunctionName", "md5");

        InputStream zipStream = Mockito.mock(InputStream.class);
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.download(URL, "", "")).thenReturn(zipStream);

        BpjmModul bpjmModul = new BpjmModul(Arrays.asList(
            new BpjmEntry(Digests.md5("domain"), Digests.md5(""), 0),
            new BpjmEntry(Digests.md5("domain"), Digests.md5("path"), 0),
            new BpjmEntry(Digests.md5("domain"), Digests.md5("path/"), 1)
        ), LAST_MODIFIED);
        BpjmModulZipReader zipReader = Mockito.mock(BpjmModulZipReader.class);
        Mockito.when(zipReader.read(zipStream)).thenReturn(bpjmModul);

        serializer = Mockito.mock(BpjmModulSerializer.class);
        Mockito.doAnswer(im -> {
            ((OutputStream)im.getArgument(1)).write(this.getClass().toString().getBytes());
            return null;
        }).when(serializer).write(Mockito.any(BpjmModul.class), Mockito.any(OutputStream.class));

        creator = new BpjmPatternFilterCreator(properties, serializer, zipReader, httpClient);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(outPath);
        System.out.println(System.currentTimeMillis());
    }

    @Test
    public void test() throws IOException {
        creator.run();

        // check modul has been correctly filtered
        ArgumentCaptor<BpjmModul> captor = ArgumentCaptor.forClass(BpjmModul.class);
        Mockito.verify(serializer).write(captor.capture(),Mockito.any(OutputStream.class));
        BpjmModul bpjmModul = captor.getValue();
        Assert.assertNotNull(bpjmModul);
        Assert.assertEquals(LAST_MODIFIED, bpjmModul.getLastModified());
        Assert.assertNotNull(bpjmModul.getEntries());
        Assert.assertEquals(2, bpjmModul.getEntries().size());
        Assert.assertArrayEquals(Digests.md5("domain"), bpjmModul.getEntries().get(0).getDomainHash());
        Assert.assertArrayEquals(Digests.md5("path"), bpjmModul.getEntries().get(0).getPathHash());
        Assert.assertEquals(0, bpjmModul.getEntries().get(0).getDepth());
        Assert.assertArrayEquals(Digests.md5("domain"), bpjmModul.getEntries().get(1).getDomainHash());
        Assert.assertArrayEquals(Digests.md5("path/"), bpjmModul.getEntries().get(1).getPathHash());
        Assert.assertEquals(1, bpjmModul.getEntries().get(1).getDepth());

        // check serializer output stream has been pointed at expected location
        Assert.assertTrue(Files.exists(outPath));
        Assert.assertArrayEquals(this.getClass().toString().getBytes(), Files.readAllBytes(outPath));
    }

}