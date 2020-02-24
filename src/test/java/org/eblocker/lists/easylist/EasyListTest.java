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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.junit.Rule;
import org.junit.Test;

import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.FilterParser;
import org.eblocker.server.icap.filter.FilterResult;
import org.eblocker.server.icap.filter.FilterStore;
import org.eblocker.server.icap.filter.json.JSONMarshaller;
import org.eblocker.server.icap.filter.learning.LearningFilter;


public class EasyListTest {
	@Rule public FileResource config = new FileResource("lists.properties");

	/**
	 * Tests that EasyLists can be parsed and JSON of a minimum size is produced.
	 * This should ensure, that we do not have broken or empty lists.
	 * 
	 * @throws IOException
	 */
	@Test
	public void testEasyLists() throws Exception {
		Properties properties = new Properties();
		properties.load(config.getInputStream());

		EasyListConfiguration configuration = new EasyListConfiguration(properties);

		for (EasyListDescription list : configuration.getLists()) {
			testEasyListParsingAndSerialization(list.getFilename(), list.getMinimumBytesJSON());
			testChecksum(list.getFilename());
			MaxFilterSizeValidator.verifyFilterSize(list);
		}
	}

	private void testChecksum(String filename) throws Exception {
		ChecksumValidator validator = new ChecksumValidator();
		FileInputStream input = new FileInputStream(filename);
		assertEquals(ChecksumValidationResult.OK, validator.validate(input));
	}

	private void testEasyListParsingAndSerialization(String listFile, long minimumJsonByteSize) throws IOException {
		LearningFilter learningFilter = new LearningFilter(null, false) {
			@Override
			protected FilterResult doLearn(FilterResult result,	TransactionContext context) {
				return result;
			}
		};

		FilterParser parser = new FilterParser(EasyListLineParser::new);
		FilterStore store = new FilterStore(learningFilter);
		store.update(parser.parse(new FileInputStream(new File(listFile))));

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		JSONMarshaller.marshall(store, baos);
		assertTrue("Expected JSON output of " + listFile + " to be at least " + minimumJsonByteSize + " bytes. Got only " + baos.size() + " bytes.",
				baos.size() > minimumJsonByteSize);
	}
}
