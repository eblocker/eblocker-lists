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
package org.eblocker.lists.easylist;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class EasyListRuleTest implements TransactionContext {

    private final String url;

    private final String referrer;

    private final String accept;

    private final Set<Decision> allowedDecisions;

    public EasyListRuleTest(String url, String referrer, String accept, Decision decision) {
        this.url = url;
        this.referrer = referrer;
        this.accept = accept;
        this.allowedDecisions = Set.of(decision);
    }

    public EasyListRuleTest(String property) {
        String[] fields = property.split("\\s+");
        this.url = fields[0];
        this.referrer = fields.length > 1 ? (fields[1].equals("null") ? null : fields[1]) : null;
        this.accept   = fields.length > 2 ? (fields[2].equals("null") ? null : fields[2]) : null;
        this.allowedDecisions = fields.length > 3 ? parseDecisions(fields[3]) : Set.of(Decision.NO_DECISION, Decision.PASS);
    }

    /**
     * Parse allowed decisions from a string. Decisions can be combined with "|", e.g.
     * "NO_DECISION|PASS"
     * @param definition
     * @return
     */
    private Set<Decision> parseDecisions(String definition) {
        return Arrays.stream(definition.split("\\|"))
            .map(Decision::valueOf)
            .collect(Collectors.toSet());
    }

    public boolean isAllowed(Decision decision) {
        return allowedDecisions.contains(decision);
    }

    @Override
    public String getSessionId() {
        return null;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getDomain() {
        return null;
    }

    public String getReferrer() {
        return referrer;
    }

    @Override
    public String getReferrerHostname() {
        return null;
    }

    @Override
    public String getAccept() {
        return accept;
    }

    @Override
    public Decision getDecision() {
        throw new RuntimeException("getDecision() not implemented");
    }

    @Override
    public String getRedirectTarget() {
        return null;
    }

    @Override
    public boolean isThirdParty() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(url).append("|").append(referrer).append("|").append(accept).append("|").append(allowedDecisions);
        return s.toString();
    }
}
