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

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DomainSetTest {

    @Test
    public void testAdd() {
        List<String> domains = Arrays.asList(".domain.b", ".domain2.b", ".domain3.x", ".api.domain.b", ".web.api.domain3.x");

        Collection<List<String>> permutations = Collections2.permutations(domains);
        for(List<String> permutation : permutations) {
            DomainSet set = new DomainSet();
            set.add(permutation);
            Assert.assertEquals(Sets.newHashSet(".domain.b", ".domain2.b", ".domain3.x"), set.getDomains());
        }
    }

    @Test
    public void testAddDomainAndTwoSubdomains() {
        List<String> domains = Arrays.asList(".www1.example.com", ".example.com", ".www2.example.com");
        DomainSet set = new DomainSet();
        set.add(domains);
        Assert.assertEquals(Sets.newHashSet(".example.com"), set.getDomains());
    }

    @Test
    public void testRemove() {
        DomainSet domains = new DomainSet();

        domains.add(Arrays.asList(".api.eblocker.com", ".api.google.com", ".www.google.com", ".awesome.fancy.pants.org"));
        Assert.assertEquals(Sets.newHashSet(".api.eblocker.com", ".api.google.com", ".www.google.com", ".awesome.fancy.pants.org"), domains.getDomains());

        domains.remove(Arrays.asList(".google.com", ".non.existant", ".pants.org"));
        Assert.assertEquals(Sets.newHashSet(".api.eblocker.com"), domains.getDomains());

        domains.remove(Arrays.asList(".eblocker.com"));
        Assert.assertEquals(Collections.emptySet(), domains.getDomains());
    }
}