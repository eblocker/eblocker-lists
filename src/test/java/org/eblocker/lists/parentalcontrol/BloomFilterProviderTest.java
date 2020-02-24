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
package org.eblocker.lists.parentalcontrol;

import org.eblocker.server.common.blacklist.BlacklistCompiler;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class BloomFilterProviderTest {

    @Test
    public void test() throws IOException {
        BlacklistCompiler blacklistCompiler = Mockito.mock(BlacklistCompiler.class);
        ObjectMapper objectMapper = new ObjectMapper();
        BloomFilterProvider provider = new BloomFilterProvider(blacklistCompiler, objectMapper);

        List<Filter> filters = Arrays.asList(
            filter(0, Category.ADS, new DomainBlacklist(LocalDateTime.now(), Collections.singleton("ads.com"))),
            filter(1, Category.TRACKERS, new DomainBlacklist(LocalDateTime.now(), Collections.singleton("tracker.com"))),
            filter(2, Category.PARENTAL_CONTROL, new DomainBlacklist(LocalDateTime.now(), Collections.singleton("parental.com"))),
            filter(3, Category.ADS, new HashBlacklist(LocalDateTime.now(), Collections.emptySet(), "md5")),
            filter(3, Category.TRACKERS, new HashBlacklist(LocalDateTime.now(), Collections.emptySet(), "sha1"))
        );

        InputStream in = ClassLoader.getSystemResourceAsStream("filtersourcemetadata/bloom.json");
        ParentalControlFilterSourceMetaData sourceMetadata = objectMapper.readValue(in, ParentalControlFilterSourceMetaData.class);
        Filter filter = provider.createFilter(sourceMetadata, filters);

        Assert.assertEquals(Integer.valueOf(235), filter.getMetaData().getId());
        Assert.assertEquals("test", filter.getMetaData().getDescription().get("en"));
        Assert.assertEquals("bloom", filter.getMetaData().getFilterType());
        Assert.assertEquals("test", filter.getMetaData().getName().get("en"));
        Assert.assertEquals(Category.ADS_TRACKERS_BLOOM_FILTER, filter.getMetaData().getCategory());
        Assert.assertNotNull(filter.getMetaData().getVersion());
        Assert.assertNotNull(filter.getMetaData().getFilenames());
        Assert.assertEquals(1, filter.getMetaData().getFilenames().size());
        Assert.assertEquals("lists/squidblacklist.org/test.bloom", filter.getMetaData().getFilenames().get(0));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(blacklistCompiler).compile(Mockito.eq(235), Mockito.eq("test"), captor.capture(), Mockito.anyString(), Mockito.eq("lists/squidblacklist.org/test.bloom"));
        Assert.assertEquals(Sets.newHashSet("ads.com", "tracker.com"), new HashSet<>(captor.getValue()));
    }

    private Filter filter(int id, Category category, Blacklist blacklist) {
        ParentalControlFilterMetaData metadata = new ParentalControlFilterMetaData();
        metadata.setId(id);
        metadata.setCategory(category);
        return new Filter(metadata, blacklist);
    }
}