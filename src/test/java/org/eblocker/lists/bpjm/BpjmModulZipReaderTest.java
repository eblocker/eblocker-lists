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

import org.eblocker.server.icap.filter.bpjm.BpjmModul;
import org.eblocker.lists.util.Digests;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class BpjmModulZipReaderTest {

    @Test
    public void test() throws IOException {
        BpjmModul bpjmModul;
        BpjmModulZipReader zipReader = new BpjmModulZipReader();
        try (InputStream in = ClassLoader.getSystemResourceAsStream("bpjm-modul.zip")) {
            bpjmModul = zipReader.read(in);
        }

        Assert.assertNotNull(bpjmModul);
        Assert.assertEquals(3, bpjmModul.getEntries().size());
        Assert.assertArrayEquals(Digests.md5("domain"), bpjmModul.getEntries().get(0).getDomainHash());
        Assert.assertArrayEquals(Digests.md5(""), bpjmModul.getEntries().get(0).getPathHash());
        Assert.assertEquals(0, bpjmModul.getEntries().get(0).getDepth());
        Assert.assertArrayEquals(Digests.md5("domain"), bpjmModul.getEntries().get(1).getDomainHash());
        Assert.assertArrayEquals(Digests.md5("path"), bpjmModul.getEntries().get(1).getPathHash());
        Assert.assertEquals(0, bpjmModul.getEntries().get(1).getDepth());
        Assert.assertArrayEquals(Digests.md5("domain"), bpjmModul.getEntries().get(2).getDomainHash());
        Assert.assertArrayEquals(Digests.md5("path/"), bpjmModul.getEntries().get(2).getPathHash());
        Assert.assertEquals(1, bpjmModul.getEntries().get(2).getDepth());
    }

}