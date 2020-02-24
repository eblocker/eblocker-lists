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
import org.eblocker.server.common.data.parentalcontrol.QueryTransformation;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public class ParentalControlFilterSourceMetaData {
    private Integer id;
    private String key;
    private Map<String, String> name;
    private Map<String, String> description;
    private Category category;
    private String version;
    private String filterType;
    private String provider;
    private List<QueryTransformation> queryTransformations;
    private JsonNode providerParameters;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Map<String, String> getName() {
        return name;
    }

    public void setName(Map<String, String> name) {
        this.name = name;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public List<QueryTransformation> getQueryTransformations() {
        return queryTransformations;
    }

    public void setQueryTransformations(List<QueryTransformation> queryTransformations) {
        this.queryTransformations = queryTransformations;
    }

    public JsonNode getProviderParameters() {
        return providerParameters;
    }

    public void setProviderParameters(JsonNode providerParameters) {
        this.providerParameters = providerParameters;
    }
}
