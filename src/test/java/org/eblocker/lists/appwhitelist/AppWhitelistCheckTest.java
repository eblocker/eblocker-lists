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
package org.eblocker.lists.appwhitelist;

import org.eblocker.server.common.blacklist.BlacklistCompiler;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppWhitelistCheckTest {

    private Path appWhitelistsPath;
    private Path allowedDomainsPath;
    private Path domainFilterMetadataPath;
    private Path[] domainFilterPaths;
    private Path easylistPath;
    private Path temporaryPath;

    private AppWhitelistCheck appWhitelistCheck;

    @Before
    public void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // create app whitelist
        List<AppWhitelistModule> appWhitelists = Arrays.asList(
            createWhitelist(0, "whitelist0.com"),
            createWhitelist(1, "whitelist1.com", "ads.com", "trackers.com"),
            createWhitelist(2, "whitelist2.com", "ads.com", "advanced-ads.com", "trackers.com",
                "advanced-trackers.com"));

        appWhitelistsPath = Files.createTempFile("appWhitelists", null);
        objectMapper.writeValue(appWhitelistsPath.toFile(), appWhitelists);

        // touch allowed domains file
        allowedDomainsPath = Files.createTempFile("allowedDomains", null);

        // create domain filters
        BlacklistCompiler compiler = new BlacklistCompiler();
        domainFilterPaths = new Path[] {
                Files.createTempFile("domainFilter", null),
                Files.createTempFile("domainFilter", null),
                Files.createTempFile("domainFilter", null),
        };
        temporaryPath = Files.createTempFile("tmpFilter", null);
        compiler.compile(0, "domainfilter0", Arrays.asList(".ads.com", "advanced-ads.com"), domainFilterPaths[0].toString(), temporaryPath.toString());
        compiler.compileHashFilter(1, "domainfilter1", "md5",
                Collections.singletonList(Hashing.md5().hashString(".trackers.com", StandardCharsets.UTF_8).asBytes()),
                domainFilterPaths[1].toString(), temporaryPath.toString());
        compiler.compileHashFilter(2, "domainfilter2", "sha1",
                Collections.singletonList(Hashing.sha1().hashString(".advanced-trackers.com", StandardCharsets.UTF_8).asBytes()),
                domainFilterPaths[2].toString(), temporaryPath.toString());


        // create domain filter metadata
        domainFilterMetadataPath = Files.createTempFile("domainFilterMetadata", null);
        List<ParentalControlFilterMetaData> domainFilterMetadata = Arrays.asList(
                createDomainFilterMetadata(0, Category.ADS, "domainblacklist/string", "blacklist", domainFilterPaths[0]),
                createDomainFilterMetadata(1, Category.TRACKERS, "domainblacklist/md5", "blacklist", domainFilterPaths[1]),
                createDomainFilterMetadata(2, Category.TRACKERS, "domainblacklist/sha1", "blacklist", domainFilterPaths[2]),
                createDomainFilterMetadata(3, Category.ADS, "domainblacklist/string", "whitelist", null),
                createDomainFilterMetadata(4, Category.PARENTAL_CONTROL, "domainblacklist/string", "blacklist", null));
        objectMapper.writeValue(domainFilterMetadataPath.toFile(), domainFilterMetadata);

        // create easylist filter
        easylistPath = Files.createTempFile("easylist", null);
        Files.write(easylistPath, "||advanced-ads.com^\n".getBytes());

        appWhitelistCheck = new AppWhitelistCheck();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(appWhitelistsPath);
        Files.deleteIfExists(allowedDomainsPath);
        Files.deleteIfExists(domainFilterMetadataPath);
        for(Path path : domainFilterPaths) {
            Files.deleteIfExists(path);
        }
        Files.deleteIfExists(easylistPath);
        Files.deleteIfExists(temporaryPath);
    }

    @Test
    public void testSuccess() throws IOException {
        Files.write(allowedDomainsPath, "ads.com\ntrackers.com\nadvanced-ads.com\nadvanced-trackers.com\n".getBytes());
        AppWhitelistCheck.Result result = appWhitelistCheck.run(appWhitelistsPath.toString(), allowedDomainsPath.toString(), domainFilterMetadataPath.toString(), Collections.singletonList(easylistPath.toString()));
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockedDomainsByAppWhitelistIds());
        Assert.assertEquals(3, result.getBlockedDomainsByAppWhitelistIds().size());
        Assert.assertEquals(4, result.getNumberOfBlockedDomains());
        Assert.assertEquals(4, result.getNumberOfAllowedBlockedDomains());

        List<AppWhitelistCheck.BlockedDomain> blockedDomains = result.getBlockedDomainsByAppWhitelistIds().get(0);
        Assert.assertNotNull(blockedDomains);
        Assert.assertEquals(0, blockedDomains.size());

        blockedDomains = result.getBlockedDomainsByAppWhitelistIds().get(1);
        Assert.assertNotNull(blockedDomains);
        Assert.assertEquals(2, blockedDomains.size());
        assertContains("ads.com", true, "domain filter id 0", blockedDomains);
        assertContains("trackers.com", true, "domain filter id 1", blockedDomains);

        blockedDomains = result.getBlockedDomainsByAppWhitelistIds().get(2);
        Assert.assertNotNull(blockedDomains);
        Assert.assertEquals(5, blockedDomains.size());
        assertContains("ads.com", true, "domain filter id 0", blockedDomains);
        assertContains("advanced-ads.com", true, "domain filter id 0", blockedDomains);
        assertContains("advanced-ads.com", true, easylistPath.toString(), blockedDomains);
        assertContains("advanced-trackers.com", true, "domain filter id 2", blockedDomains);
        assertContains("trackers.com", true, "domain filter id 1", blockedDomains);
    }

    @Test
    public void testFail() throws IOException {
        AppWhitelistCheck.Result result = appWhitelistCheck.run(appWhitelistsPath.toString(), allowedDomainsPath.toString(), domainFilterMetadataPath.toString(), Collections.singletonList(easylistPath.toString()));
        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getBlockedDomainsByAppWhitelistIds());
        Assert.assertEquals(3, result.getBlockedDomainsByAppWhitelistIds().size());
        Assert.assertEquals(4, result.getNumberOfBlockedDomains());
        Assert.assertEquals(0, result.getNumberOfAllowedBlockedDomains());

        List<AppWhitelistCheck.BlockedDomain> blockedDomains = result.getBlockedDomainsByAppWhitelistIds().get(0);
        Assert.assertNotNull(blockedDomains);
        Assert.assertEquals(0, blockedDomains.size());

        blockedDomains = result.getBlockedDomainsByAppWhitelistIds().get(1);
        Assert.assertNotNull(blockedDomains);
        Assert.assertEquals(2, blockedDomains.size());
        assertContains("ads.com", false, "domain filter id 0", blockedDomains);
        assertContains("trackers.com", false, "domain filter id 1", blockedDomains);

        blockedDomains = result.getBlockedDomainsByAppWhitelistIds().get(2);
        Assert.assertNotNull(blockedDomains);
        Assert.assertEquals(5, blockedDomains.size());
        assertContains("ads.com", false, "domain filter id 0", blockedDomains);
        assertContains("advanced-ads.com", false, "domain filter id 0", blockedDomains);
        assertContains("advanced-ads.com", false, easylistPath.toString(), blockedDomains);
        assertContains("advanced-trackers.com", false, "domain filter id 2", blockedDomains);
        assertContains("trackers.com", false, "domain filter id 1", blockedDomains);
    }

    @Test
    public void testPrint() throws IOException {
        // not actually checking output but just check it does not throw exceptions
        AppWhitelistCheck.Result result = appWhitelistCheck.run(appWhitelistsPath.toString(), allowedDomainsPath.toString(), domainFilterMetadataPath.toString(), Collections.singletonList(easylistPath.toString()));
        result.print();
    }

    private void assertContains(String domain, boolean allowed, String blockedBy, List<AppWhitelistCheck.BlockedDomain> blockedDomains) {
        for(AppWhitelistCheck.BlockedDomain blockedDomain : blockedDomains) {
            if (blockedDomain.getDomain().equals(domain)
                && blockedDomain.isAllowed() == allowed
                && blockedDomain.getBlockedBy().equals(blockedBy)) {
                return;
            }
        }
        Assert.fail("Expected blocked domain: " + domain + " allowed: " + allowed + " blockedBy: " + blockedBy + " not found!");
    }

    private AppWhitelistModule createWhitelist(int id, String... domains) {
        return new AppWhitelistModule(id, "whitelist" + id, null, Arrays.asList(domains), null, null, null, null, null, null, null, null, null, null);
    }

    private ParentalControlFilterMetaData createDomainFilterMetadata(int id, Category category, String format, String type, Path path) {
        ParentalControlFilterMetaData metadata = new ParentalControlFilterMetaData();
        metadata.setId(id);
        metadata.setCategory(category);
        metadata.setFormat(format);
        metadata.setFilterType(type);
        if (path != null) {
            metadata.setFilenames(Collections.singletonList(path.toString()));
        }
        return metadata;
    }
}
