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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

/**
 * A configuration of several EasyLists.
 */
public class EasyListConfiguration {
	private static final Logger LOG = LoggerFactory.getLogger(EasyListConfiguration.class);

	private List<EasyListDescription> lists = new Vector<>();
	private Properties properties;
	
	/**
	 * Parses a configuration from a properties list
	 */
	public EasyListConfiguration(Properties props) throws IOException {
		properties = props;
		String[] easyLists = properties.getProperty("list.all").split("\\s+");
		String directory = getDirectory();
		int maxFilterSize = Integer.parseInt(properties.getProperty("list.max_filter_size"));
		for (String list : easyLists) {
			String filename = directory + "/" + getListParameter(list, "filename");
			String url = getListParameter(list, "url");
			long minimumBytesJSON = Long.parseLong(getListParameter(list, "minimum_bytes_json"));
			int maxAgeDays = Integer.parseInt(getListParameter(list, "max_age_days"));
			String hasChecksumValue = getListParameter(list, "has_checksum");
			boolean hasChecksum = hasChecksumValue == null ? true : Boolean.parseBoolean(hasChecksumValue);
			Set<EasyListRuleTest> easyListRuleTests = new HashSet<>();
			int i = 0;
			while (true) {
				String ruleTestResource = getListParameter(list, "ruletest", ++i);
				if (ruleTestResource == null) {
					break;
				}
                easyListRuleTests.addAll(new EasyListRuleTestResource(ruleTestResource).getRuleTests());
            }
			lists.add(new EasyListDescription(url, filename, minimumBytesJSON, maxAgeDays, maxFilterSize, hasChecksum, easyListRuleTests));
			LOG.info("Loaded EasyListConfiguration {} with {} rule tests", list, easyListRuleTests.size());
		}
	}
	
	private String getListParameter(String list, String key) {
		return properties.getProperty(String.format("list.%s.%s", list, key));
	}

	private String getListParameter(String list, String key, int i) {
		return properties.getProperty(String.format("list.%s.%s.%d", list, key, i));
	}

	public String getDirectory() {
		return properties.getProperty("list.directory");
	}
	
	public List<EasyListDescription> getLists() {
		return lists;
	}
}
