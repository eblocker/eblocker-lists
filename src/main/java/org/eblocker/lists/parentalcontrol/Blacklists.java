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

import org.eblocker.server.common.util.ByteArrays;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class Blacklists {

    private Blacklists() {
    }

    public static DomainBlacklist mergeBlacklist(DomainBlacklist a, DomainBlacklist b) {
        LocalDateTime t = max(a.getDate(), b.getDate());

        Set<String> domains = new HashSet<>();
        domains.addAll(a.getList());
        domains.addAll(b.getList());

        return new DomainBlacklist(t, domains);
    }

    public static HashBlacklist mergeBlacklist(HashBlacklist a, HashBlacklist b) {
        if (!a.getHashFunctionName().equals(b.getHashFunctionName())) {
            throw new IllegalArgumentException("can not merge hash blacklists with different hashing function");
        }

        LocalDateTime t = max(a.getDate(), b.getDate());
        Set<byte[]> hashes = mergeByteSets(a.getList(), b.getList());
        return new HashBlacklist(t, hashes, a.getHashFunctionName());
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return b.isAfter(a) ? b : a;
    }

    private static Set<byte[]> mergeByteSets(Set<byte[]> a, Set<byte[]> b) {
        Set<byte[]> mergedSet = new TreeSet<>(ByteArrays::compare);
        mergedSet.addAll(a);
        mergedSet.addAll(b);
        return mergedSet;
    }
}
