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
import org.eblocker.server.common.util.ByteArrays;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DomainFilterProviderTest {

    private BlacklistCompiler blacklistCompiler;
    private ObjectMapper objectMapper;

    private DomainFilterProvider provider;

    @Before
    public void setUp() {
        Map<String, BlacklistProvider> providersByName = new HashMap<>();
        providersByName.put("a", new MockProvider(LocalDateTime.now(), Sets.newHashSet(".domain.a", ".domain2.a", ".domain3.x", ".api.domain2.a")));
        providersByName.put("b", new MockProvider(LocalDateTime.now(), Sets.newHashSet(".domain.b", ".domain2.b", ".domain3.x", ".api.domain.b", ".web.api.domain3.x")));
        providersByName.put("e", new MockHashProvider(LocalDateTime.now(), hash(Hashing.md5(), ".domain.md5", "exclude.domain.md5"), "md5"));
        providersByName.put("eex", new MockHashProvider(LocalDateTime.now(), hash(Hashing.md5(), "exclude.domain.md5", ".domain.a"), "md5"));
        providersByName.put("f", new MockHashProvider(LocalDateTime.now(), hash(Hashing.sha1(), ".domain.sha1", "exclude.domain.sha1"), "sha1"));
        providersByName.put("fex", new MockHashProvider(LocalDateTime.now(), hash(Hashing.sha1(), "exclude.domain.sha1", ".domain.a"), "sha1"));

        blacklistCompiler = Mockito.mock(BlacklistCompiler.class);
        objectMapper = new ObjectMapper();
        provider = new DomainFilterProvider(blacklistCompiler, objectMapper, providersByName);
    }

    @Test
    public void testSingleBlacklist() throws IOException {
        ParentalControlFilterSourceMetaData sourceMetadata = readMetaData("single.json");
        Filter filter = provider.createFilter(sourceMetadata, null);

        Assert.assertEquals(Integer.valueOf(0), filter.getMetaData().getId());
        Assert.assertEquals("description", filter.getMetaData().getDescription().get("en"));
        Assert.assertEquals("blacklist", filter.getMetaData().getFilterType());
        Assert.assertEquals("test-filter-a", filter.getMetaData().getName().get("en"));
        Assert.assertEquals(Category.ADS, filter.getMetaData().getCategory());
        Assert.assertEquals("20180101", filter.getMetaData().getVersion());
        Assert.assertNotNull(filter.getMetaData().getFilenames());
        Assert.assertEquals(2, filter.getMetaData().getFilenames().size());
        Assert.assertEquals("lists/squidblacklist.org/a.filter", filter.getMetaData().getFilenames().get(0));
        Assert.assertEquals("lists/squidblacklist.org/a.bloom", filter.getMetaData().getFilenames().get(1));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(blacklistCompiler).compile(Mockito.eq(0), Mockito.eq("a"), captor.capture(), Mockito.eq("lists/squidblacklist.org/a.filter"), Mockito.eq("lists/squidblacklist.org/a.bloom"));
        Assert.assertEquals(Sets.newHashSet(".domain.a", ".domain2.a", ".domain3.x"), new HashSet<>(captor.getValue()));
    }

    @Test
    public void testAddBlacklists() throws IOException {
        ParentalControlFilterSourceMetaData sourceMetadata = readMetaData("add.json");
        Filter filter = provider.createFilter(sourceMetadata, null);

        Assert.assertEquals(Integer.valueOf(2), filter.getMetaData().getId());
        Assert.assertEquals("description", filter.getMetaData().getDescription().get("en"));
        Assert.assertEquals("blacklist", filter.getMetaData().getFilterType());
        Assert.assertEquals("test-filter-c", filter.getMetaData().getName().get("en"));
        Assert.assertEquals(Category.PARENTAL_CONTROL, filter.getMetaData().getCategory());
        Assert.assertEquals("20180201", filter.getMetaData().getVersion());
        Assert.assertNotNull(filter.getMetaData().getFilenames());
        Assert.assertEquals(2, filter.getMetaData().getFilenames().size());
        Assert.assertEquals("lists/squidblacklist.org/c.filter", filter.getMetaData().getFilenames().get(0));
        Assert.assertEquals("lists/squidblacklist.org/c.bloom", filter.getMetaData().getFilenames().get(1));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(blacklistCompiler).compile(Mockito.eq(2), Mockito.eq("c"), captor.capture(), Mockito.eq("lists/squidblacklist.org/c.filter"), Mockito.eq("lists/squidblacklist.org/c.bloom"));
        Assert.assertEquals(Sets.newHashSet(".domain.a", ".domain2.a", ".domain.b", ".domain2.b", ".domain3.x"), new HashSet<>(captor.getValue()));
    }

    @Test
    public void testExcludeBlacklist() throws IOException {
        ParentalControlFilterSourceMetaData sourceMetadata = readMetaData("exclude.json");
        Filter filter = provider.createFilter(sourceMetadata, null);

        Assert.assertEquals(Integer.valueOf(3), filter.getMetaData().getId());
        Assert.assertEquals("description", filter.getMetaData().getDescription().get("en"));
        Assert.assertEquals("blacklist", filter.getMetaData().getFilterType());
        Assert.assertEquals("test-filter-d", filter.getMetaData().getName().get("en"));
        Assert.assertEquals(Category.PARENTAL_CONTROL, filter.getMetaData().getCategory());
        Assert.assertEquals("20180201", filter.getMetaData().getVersion());
        Assert.assertNotNull(filter.getMetaData().getFilenames());
        Assert.assertEquals(2, filter.getMetaData().getFilenames().size());
        Assert.assertEquals("lists/squidblacklist.org/d.filter", filter.getMetaData().getFilenames().get(0));
        Assert.assertEquals("lists/squidblacklist.org/d.bloom", filter.getMetaData().getFilenames().get(1));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(blacklistCompiler).compile(Mockito.eq(3), Mockito.eq("d"), captor.capture(), Mockito.eq("lists/squidblacklist.org/d.filter"), Mockito.eq("lists/squidblacklist.org/d.bloom"));
        Assert.assertEquals(Sets.newHashSet(".domain.b", ".domain2.b"), new HashSet<>(captor.getValue()));
    }

    @Test
    public void testMd5Blacklist() throws IOException {
        ParentalControlFilterSourceMetaData sourceMetadata = readMetaData("md5.json");
        Filter filter = provider.createFilter(sourceMetadata, null);

        Assert.assertEquals(Integer.valueOf(4), filter.getMetaData().getId());
        Assert.assertEquals("description", filter.getMetaData().getDescription().get("en"));
        Assert.assertEquals("blacklist", filter.getMetaData().getFilterType());
        Assert.assertEquals("test-filter-e", filter.getMetaData().getName().get("en"));
        Assert.assertEquals(Category.PARENTAL_CONTROL, filter.getMetaData().getCategory());
        Assert.assertNotNull(filter.getMetaData().getVersion());
        Assert.assertNotNull(filter.getMetaData().getFilenames());
        Assert.assertEquals(2, filter.getMetaData().getFilenames().size());
        Assert.assertEquals("lists/squidblacklist.org/e.filter", filter.getMetaData().getFilenames().get(0));
        Assert.assertEquals("lists/squidblacklist.org/e.bloom", filter.getMetaData().getFilenames().get(1));

        ArgumentCaptor<List<byte[]>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(blacklistCompiler).compileHashFilter(Mockito.eq(4), Mockito.eq("e"), Mockito.eq("md5"), captor.capture(), Mockito.eq("lists/squidblacklist.org/e.filter"), Mockito.eq("lists/squidblacklist.org/e.bloom"));
        Assert.assertEquals(newTreeSet(hash(Hashing.md5(), ".domain2.a", ".domain3.x", ".api.domain2.a", ".domain.md5")), newTreeSet(captor.getValue()));
    }

    @Test
    public void testSha1Blacklist() throws IOException {
        ParentalControlFilterSourceMetaData sourceMetadata = readMetaData("sha1.json");
        Filter filter = provider.createFilter(sourceMetadata, null);

        Assert.assertEquals(Integer.valueOf(5), filter.getMetaData().getId());
        Assert.assertEquals("description", filter.getMetaData().getDescription().get("en"));
        Assert.assertEquals("blacklist", filter.getMetaData().getFilterType());
        Assert.assertEquals("test-filter-f", filter.getMetaData().getName().get("en"));
        Assert.assertEquals(Category.PARENTAL_CONTROL, filter.getMetaData().getCategory());
        Assert.assertNotNull(filter.getMetaData().getVersion());
        Assert.assertNotNull(filter.getMetaData().getFilenames());
        Assert.assertEquals(2, filter.getMetaData().getFilenames().size());
        Assert.assertEquals("lists/squidblacklist.org/f.filter", filter.getMetaData().getFilenames().get(0));
        Assert.assertEquals("lists/squidblacklist.org/f.bloom", filter.getMetaData().getFilenames().get(1));

        ArgumentCaptor<List<byte[]>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(blacklistCompiler).compileHashFilter(Mockito.eq(5), Mockito.eq("f"), Mockito.eq("sha1"), captor.capture(), Mockito.eq("lists/squidblacklist.org/f.filter"), Mockito.eq("lists/squidblacklist.org/f.bloom"));
        Assert.assertEquals(newTreeSet(hash(Hashing.sha1(), ".domain2.a", ".domain3.x", ".api.domain2.a", ".domain.sha1")), newTreeSet(captor.getValue()));
    }

    private ParentalControlFilterSourceMetaData readMetaData(String name) throws IOException {
        InputStream in = ClassLoader.getSystemResourceAsStream("filtersourcemetadata/" + name);
        return objectMapper.readValue(in, ParentalControlFilterSourceMetaData.class);
    }

    private Set<byte[]> hash(HashFunction hashFunction, String... values) {
        Set<byte[]> hashes = new HashSet<>();
        for(String value : values) {
            hashes.add(hashFunction.hashString(value, Charsets.UTF_8).asBytes());
        }
        return hashes;
    }

    private TreeSet<byte[]> newTreeSet(Collection<byte[]> values) {
        TreeSet<byte[]> treeSet = new TreeSet<>(ByteArrays::compare);
        treeSet.addAll(values);
        return treeSet;
    }

    private class MockProvider implements BlacklistProvider {
        private LocalDateTime dateTime;
        private Set<String> domains;

        public MockProvider(LocalDateTime dateTime, Set<String> domains) {
            this.dateTime = dateTime;
            this.domains = domains;
        }

        @Override
        public Blacklist createBlacklist(JsonNode sourceParameters) {
            JsonNode node = sourceParameters.get("date");
            if (node != null) {
                dateTime = LocalDate.parse(node.asText(), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            }
            return new DomainBlacklist(dateTime, domains);
        }
    }

    private class MockHashProvider implements BlacklistProvider {
        private LocalDateTime dateTime;
        private Set<byte[]> hashes;
        private String hashFunctionName;

        public MockHashProvider(LocalDateTime dateTime, Set<byte[]> hashes, String hashFunctionName) {
            this.dateTime = dateTime;
            this.hashes = hashes;
            this.hashFunctionName = hashFunctionName;
        }

        @Override
        public Blacklist createBlacklist(JsonNode sourceParameters) {
            JsonNode node = sourceParameters.get("date");
            if (node != null) {
                dateTime = LocalDate.parse(node.asText(), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
            }
            return new HashBlacklist(dateTime, hashes, hashFunctionName);
        }
    }
}