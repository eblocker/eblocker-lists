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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

public class HostsFileProviderTest {

    private ObjectMapper objectMapper;
    private HttpClient httpClient;

    @Before
    public void setUp() throws IOException {
        objectMapper = new ObjectMapper();

        httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.download("test-url", "", "")).thenReturn(new ByteArrayInputStream((
                "# hpHosts - Ad and Tracking servers only\n" +
                        "#\n" +
                        "# The following are hosts in the hpHosts database with the ATS classification ONLY.\n" +
                        "# This file will NOT protect you against malicious domains\n" +
                        "#\n" +
                        "127.0.0.1 localhost #IPv4 localhost\n" +
                        "::1 localhost #IPv6 localhost\n" +
                        "#\n" +
                        "# AD SERVERS START HERE\n" +
                        "#\n" +
                        "127.0.0.1\t005.free-counter.co.uk\n" +
                        "127.0.0.1\t006.free-adult-counters.x-xtra.com\n" +
                        "127.0.0.1\t006.free-counter.co.uk\n" +
                        "127.0.0.1\t007.free-counter.co.uk\n" +
                        "127.0.0.1\t007.go2cloud.org\n" +
                        "127.0.0.1\t0075-7112-e7eb-f9b9.reporo.net").getBytes()));
    }

    @Test
    public void testGetDomains() {
        HostsFileProvider provider = new HostsFileProvider(httpClient);
        DomainBlacklist blacklist = provider.createBlacklist(objectMapper.valueToTree(Collections.singletonMap("url", "test-url")));

        Assert.assertTrue(Duration.between(LocalDateTime.now(), blacklist.getDate()).toMinutes() < 1);
        Assert.assertEquals(Sets.newHashSet(".005.free-counter.co.uk", ".006.free-adult-counters.x-xtra.com", ".006.free-counter.co.uk", ".007.free-counter.co.uk", ".007.go2cloud.org", ".0075-7112-e7eb-f9b9.reporo.net"), blacklist.getList());
    }

}