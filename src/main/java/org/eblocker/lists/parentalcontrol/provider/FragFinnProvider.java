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

import org.eblocker.lists.parentalcontrol.HashBlacklist;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.lists.parentalcontrol.BlacklistProvider;
import org.eblocker.lists.util.HttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FragFinnProvider implements BlacklistProvider {

    private static final Logger log = LoggerFactory.getLogger(FragFinnProvider.class);

    private final HttpClient httpClient;

    public FragFinnProvider(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HashBlacklist createBlacklist(JsonNode sourceParameters) {
        try {
            InputStream in;
            JsonNode node = sourceParameters.get("url");

            if(node != null) {
                String url = node.asText();
                in = httpClient.download(url, "", "");
            } else {
                node = sourceParameters.get("resource");
                if (node != null) {
                    String resourceName = node.asText();
                    SimpleResource resource = new SimpleResource(resourceName);
                    in = ResourceHandler.getInputStream(resource);
                } else {
                    throw new IOException("Source parameter 'url' or 'resource' needed.");
                }
            }

            JAXBContext context = JAXBContext.newInstance(Root.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            Root root = (Root) unmarshaller.unmarshal(in);
            if (root.getCount() != root.getEntries().size()) {
                log.warn("expected {} elements but got {}", root.getCount(), root.getEntries().size());
            }

            LocalDateTime now = LocalDateTime.now();
            Set<byte[]> hashes = root.getEntries().stream()
                .map(Entry::getValue)
                .map(DatatypeConverter::parseHexBinary)
                .collect(Collectors.toSet());

            return new HashBlacklist(now, hashes, "sha1");
        } catch (JAXBException e) {
            throw new FragFinnXmlException("parsing FragFINN xml failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class FragFinnXmlException extends RuntimeException {
        public FragFinnXmlException(String message, Exception e) {
            super(message, e);
        }
    }

    @XmlRootElement
    public static class Root {
        private int count;

        private List<Entry> entries;

        @XmlAttribute
        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @XmlElement(name = "u")
        public List<Entry> getEntries() {
            return entries;
        }

        public void setEntries(List<Entry> entries) {
            this.entries = entries;
        }
    }

    public static class Entry {
        private Boolean kids;
        private Boolean knowledge;
        private Boolean primary;
        private Boolean ssl;
        private String value;

        @XmlAttribute
        public Boolean getKids() {
            return kids;
        }

        public void setKids(Boolean kids) {
            this.kids = kids;
        }

        @XmlAttribute
        public Boolean getKnowledge() {
            return knowledge;
        }

        public void setKnowledge(Boolean knowledge) {
            this.knowledge = knowledge;
        }

        @XmlAttribute
        public Boolean getPrimary() {
            return primary;
        }

        public void setPrimary(Boolean primary) {
            this.primary = primary;
        }

        @XmlAttribute
        public Boolean getSsl() {
            return ssl;
        }

        public void setSsl(Boolean ssl) {
            this.ssl = ssl;
        }

        @XmlValue
        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
