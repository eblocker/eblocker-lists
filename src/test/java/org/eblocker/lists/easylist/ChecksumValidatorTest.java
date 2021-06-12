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

import java.io.InputStream;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ChecksumValidatorTest {
	private ChecksumValidator validator;
	
	@Rule public FileResource easyPrivacyList      = new FileResource("easyprivacy_general.txt");
	@Rule public FileResource checksumMismatchList = new FileResource("checksum_mismatch.txt");
	@Rule public FileResource checksumMissingList  = new FileResource("checksum_missing.txt");

	@Before
	public void setUp() throws Exception {
		validator = new ChecksumValidator();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void easyPrivacy() throws Exception {
		InputStream listStream = easyPrivacyList.getInputStream();
		assertEquals(ChecksumValidationResult.OK, validator.validate(listStream));
	}
	
	@Test
	public void checksumMismatch() throws Exception {
		InputStream listStream = checksumMismatchList.getInputStream();
		assertEquals(ChecksumValidationResult.CHECKSUM_MISMATCH, validator.validate(listStream));
	}

	@Test
	public void checksumMissing() throws Exception {
		InputStream listStream = checksumMissingList.getInputStream();
		assertEquals(ChecksumValidationResult.CHECKSUM_MISSING, validator.validate(listStream));
	}

	@Test
	public void checksumNewlines() {
		Map<Integer, String> cases = Map.of(
			1, "newline at end of file",
			2, "empty line at end of file",
			3, "empty line in the middle of file",
			4, "no newline at end of file",
			5, "blank after checksum");

		cases.forEach((i, description) -> {
			String filename = String.format("checksum-%d.txt", i);
			InputStream listStream = new FileResource(filename).getInputStream();
			try {
				assertEquals(description, ChecksumValidationResult.OK, validator.validate(listStream));
			} catch (Exception e) {
				throw new RuntimeException("Could not validate list " + filename, e);
			}
		});
	}
}
