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

public class EasyListRuleTest implements TransactionContext {

    private final String url;

    private final String referrer;

    private final String accept;

    private final Decision decision;

    public EasyListRuleTest(String url, String referrer, String accept, Decision decision) {
        this.url = url;
        this.referrer = referrer;
        this.accept = accept;
        this.decision = decision;
    }

    public EasyListRuleTest(String property) {
        String[] fields = property.split("\\s+");
        this.url = fields[0];
        this.referrer = fields.length > 1 ? (fields[1].equals("null") ? null : fields[1]) : null;
        this.accept   = fields.length > 2 ? (fields[2].equals("null") ? null : fields[2]) : null;
        this.decision = fields.length > 3 ? Decision.valueOf(fields[3]) : Decision.NO_DECISION;
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
        return decision;
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
        s.append(url).append("|").append(referrer).append("|").append(accept).append("|").append(decision);
        return s.toString();
    }
}
