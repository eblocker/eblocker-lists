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

import org.eblocker.lists.malware.MalwareListException;
import org.eblocker.lists.malware.PhishtankDownloader;
import org.eblocker.lists.malware.PhishtankEntry;
import org.eblocker.lists.parentalcontrol.DomainBlacklist;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

public class PhishtankDomainProviderTest {

    @Test
    public void testCreateBlacklist() throws IOException, MalwareListException {
        PhishtankDownloader downloader = Mockito.mock(PhishtankDownloader.class);
        Mockito.when(downloader.retrieveEntries()).thenReturn(Arrays.asList(
            new PhishtankEntry("www.facebook.com.dubious.ru/login/facebook", "Facebook"),
            new PhishtankEntry("innocuous.blog.com/paypal", "PayPal"),
            new PhishtankEntry("www.e8loker.com", "eBlocker")
        ));

        PhishtankDomainProvider provider = new PhishtankDomainProvider(downloader);
        DomainBlacklist blacklist = provider.createBlacklist(null);
        Assert.assertEquals(Collections.singleton("www.e8loker.com"), blacklist.getList());
        Assert.assertNotNull(blacklist.getDate());
        Assert.assertTrue(Duration.between(LocalDateTime.now(), blacklist.getDate()).getSeconds() < 60);
    }

}