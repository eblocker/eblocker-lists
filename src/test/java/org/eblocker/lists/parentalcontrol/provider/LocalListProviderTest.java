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

import org.eblocker.lists.parentalcontrol.Blacklist;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalListProviderTest {
    private ObjectMapper objectMapper;
    private LocalListProvider provider;

    @Before
    public void setUp() {
        provider = new LocalListProvider();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void formatString() {
        JsonNode sourceParameters = getSourceParameters("local-list.txt", null);

        Blacklist blacklist = provider.createBlacklist(sourceParameters);
        Set<String> domains = blacklist.getList();
        Assert.assertEquals(2, domains.size());
        Assert.assertTrue(domains.contains("www.eblocker.com"));
        Assert.assertTrue(domains.contains("www.etracker.com"));
    }

    @Test
    public void formatSha1() {
        JsonNode sourceParameters = getSourceParameters("local-list.txt", "sha1");

        Blacklist blacklist = provider.createBlacklist(sourceParameters);
        Set<byte[]> domains = blacklist.getList();
        Assert.assertEquals(2, domains.size());

        // Convert hashes to hex strings, so Set.contains() works:
        Set<String> hashes = domains.stream()
                .map(DatatypeConverter::printHexBinary)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Assert.assertTrue(hashes.contains("d43019124096948ffe035d0c108a30be5654eab7")); // www.eblocker.com
        Assert.assertTrue(hashes.contains("be48d0b8da8a6390bdd0ea7529776808e91ada6e")); // www.etracker.com
    }

    private JsonNode getSourceParameters(String resource, String format) {
        Map<String, Object> sourceParametersMap = new HashMap<>();
        sourceParametersMap.put("resource", resource);
        if (format != null) {
            sourceParametersMap.put("format", format);
        }
        return objectMapper.valueToTree(sourceParametersMap);
    }
}
