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

import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParentalControlFilterListCreatorTest {

    private ObjectMapper objectMapper;
    private Path outputFilePath;

    @Before
    public void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        outputFilePath = Files.createTempFile("output", "json");
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(outputFilePath);
    }

    @Test
    public void testGeneration() throws IOException {
        Map<String, FilterProvider> providersByName = new LinkedHashMap<>();
        providersByName.put("provider-a", mockFilterProvider("provider-a", 100));
        providersByName.put("provider-b", mockFilterProvider("provider-b", 100));

        ParentalControlFilterListCreator creator = new ParentalControlFilterListCreator("test-provider.json", outputFilePath.toString(), objectMapper, providersByName);
        creator.run();

        List<ParentalControlFilterMetaData> metadata = objectMapper
            .readValue(outputFilePath.toFile(), new TypeReference<List<ParentalControlFilterMetaData>>() {});
        Map<Integer, ParentalControlFilterMetaData> metadataById = metadata.stream().collect(Collectors.toMap(ParentalControlFilterMetaData::getId, Function.identity()));
        Assert.assertEquals(3, metadataById.size());

        Assert.assertEquals(Integer.valueOf(0), metadataById.get(0).getId());
        Assert.assertEquals("test-filter-a0", metadataById.get(0).getName().get("en"));
        Assert.assertEquals("test-filter-a0", metadataById.get(0).getName().get("de"));
        Assert.assertEquals("description", metadataById.get(0).getDescription().get("en"));
        Assert.assertEquals("description", metadataById.get(0).getDescription().get("de"));
        Assert.assertEquals(Category.ADS, metadataById.get(0).getCategory());
        Assert.assertEquals("1", metadataById.get(0).getVersion());
        Assert.assertEquals("blacklist", metadataById.get(0).getFilterType());
        Assert.assertEquals(Collections.singletonList("provider-a-file"), metadataById.get(0).getFilenames());
        Assert.assertEquals(Integer.valueOf(100), metadataById.get(0).getSize());
        Assert.assertEquals("provider-a", metadataById.get(0).getFormat());

        Assert.assertEquals(Integer.valueOf(1), metadataById.get(1).getId());
        Assert.assertEquals("test-filter-a1", metadataById.get(1).getName().get("en"));
        Assert.assertEquals("test-filter-a1", metadataById.get(1).getName().get("de"));
        Assert.assertEquals("description", metadataById.get(1).getDescription().get("en"));
        Assert.assertEquals("description", metadataById.get(1).getDescription().get("de"));
        Assert.assertEquals(Category.TRACKERS, metadataById.get(1).getCategory());
        Assert.assertEquals("2", metadataById.get(1).getVersion());
        Assert.assertEquals("whitelist", metadataById.get(1).getFilterType());
        Assert.assertEquals(Collections.singletonList("provider-a-file"), metadataById.get(1).getFilenames());
        Assert.assertEquals(Integer.valueOf(100), metadataById.get(1).getSize());
        Assert.assertEquals("provider-a", metadataById.get(1).getFormat());

        Assert.assertEquals(Integer.valueOf(2), metadataById.get(2).getId());
        Assert.assertEquals("test-filter-b", metadataById.get(2).getName().get("en"));
        Assert.assertEquals("test-filter-b", metadataById.get(2).getName().get("de"));
        Assert.assertEquals("description", metadataById.get(2).getDescription().get("en"));
        Assert.assertEquals("description", metadataById.get(2).getDescription().get("de"));
        Assert.assertEquals(Category.ADS_TRACKERS_BLOOM_FILTER, metadataById.get(2).getCategory());
        Assert.assertEquals("3", metadataById.get(2).getVersion());
        Assert.assertEquals("bloom", metadataById.get(2).getFilterType());
        Assert.assertEquals(Collections.singletonList("provider-b-file"), metadataById.get(2).getFilenames());
        Assert.assertEquals(Integer.valueOf(100), metadataById.get(2).getSize());
        Assert.assertEquals("provider-b", metadataById.get(2).getFormat());

        ArgumentCaptor<Collection<Filter>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(providersByName.get("provider-b"), Mockito.atLeastOnce()).createFilter(Mockito.any(ParentalControlFilterSourceMetaData.class), captor.capture());
        Assert.assertEquals(2, captor.getValue().size());
        Set<Integer> ids = captor.getValue().stream().map(Filter::getMetaData).map(ParentalControlFilterMetaData::getId).collect(Collectors.toSet());
        Assert.assertEquals(Sets.newHashSet(0, 1), ids);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownProvider() throws IOException {
        ParentalControlFilterListCreator creator = new ParentalControlFilterListCreator("test-provider.json", outputFilePath.toString(), objectMapper, new HashMap<>());
        creator.run();
    }

    @Test(expected = InvalidMetaDataException.class)
    public void testFilterLimit() throws IOException {
        Map<String, FilterProvider> providersByName = new LinkedHashMap<>();
        providersByName.put("provider-a", mockFilterProvider("provider-a", 100000));
        providersByName.put("provider-b", mockFilterProvider("provider-b", 100));

        ParentalControlFilterListCreator creator = new ParentalControlFilterListCreator("test-provider.json", outputFilePath.toString(), objectMapper, providersByName);

        creator.run();
    }

    private FilterProvider mockFilterProvider(String name, int size) {
        FilterProvider provider = Mockito.mock(FilterProvider.class);
        Mockito.when(provider.createFilter(Mockito.any(ParentalControlFilterSourceMetaData.class), Mockito.anyCollection())).then(im -> {
            ParentalControlFilterSourceMetaData sourceMetadata = im.getArgument(0);
            ParentalControlFilterMetaData metadata = new ParentalControlFilterMetaData();
            metadata.setId(sourceMetadata.getId());
            metadata.setBuiltin(true);
            metadata.setCategory(sourceMetadata.getCategory());
            metadata.setDescription(sourceMetadata.getDescription());
            metadata.setDate(new Date());
            metadata.setFilenames(Collections.singletonList(name + "-file"));
            metadata.setFilterType(sourceMetadata.getFilterType());
            metadata.setFormat(name);
            metadata.setName(sourceMetadata.getName());
            metadata.setSize(size);
            metadata.setVersion(sourceMetadata.getVersion());
            return new Filter(metadata, Mockito.mock(Blacklist.class));
        });
        return provider;
    }
}