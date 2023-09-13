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
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MaxAgeValidatorTest {
	private MaxAgeValidator validator;

	@Rule
	public FileResource easyPrivacyList = new FileResource("easyprivacy_general.txt");

	@Rule
	public FileResource easyListUpdated = new FileResource("easylist-updated.txt");

	@Rule
	public FileResource easyListExpiresMissing = new FileResource("easylist-expires-missing.txt");

	@Rule
	public FileResource easyListLastModifiedMissing = new FileResource("easylist-last-modified-missing.txt");

	@Before
	public void setUp() throws Exception {
		validator = new MaxAgeValidator();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testExpired() throws Exception {
		Date now = new Date();
		InputStream input = easyPrivacyList.getInputStream();
		assertEquals(MaxAgeValidationResult.EXPIRED, validator.validate(input, now, 0)); // unless you time-travel to before 25 Jul 2016
	}

	@Test
	public void testExpiredOverridden() throws Exception {
		Date now = getDate("21 Aug 2016 11:40 UTC");
		InputStream input = easyPrivacyList.getInputStream();
		assertEquals(MaxAgeValidationResult.OK, validator.validate(input, now, 32));
	}

	@Test
	public void testValid() throws Exception {
		Date now = getDate("23 Jul 2016 9:40 UTC");
		InputStream input = easyPrivacyList.getInputStream();
		assertEquals(MaxAgeValidationResult.OK, validator.validate(input, now, 0));
	}

	@Test
	public void testUpdated() throws Exception {
		Date now = getDate("17 Mar 2021 9:40 UTC");
		InputStream input = easyListUpdated.getInputStream();
		assertEquals(MaxAgeValidationResult.OK, validator.validate(input, now, 0));
	}

	@Test
	public void testDateWithDayOfWeek() throws Exception {
		String list = "! Expires: 4 days\n" +
			"! Last modified: Mon, 28 Mar 2022 12:00:08 +0000\n";

		InputStream input = IOUtils.toInputStream(list, StandardCharsets.UTF_8);
		Date now = getDate("31 Mar 2022 16:35 UTC");
		assertEquals(MaxAgeValidationResult.OK, validator.validate(input, now, 0));

		input = IOUtils.toInputStream(list, StandardCharsets.UTF_8);
		now = getDate("1 Apr 2022 16:35 UTC");
		assertEquals(MaxAgeValidationResult.EXPIRED, validator.validate(input, now, 0));
	}

	@Test
	public void testExpiresMissing() throws Exception {
		Date now = new Date();
		InputStream input = easyListExpiresMissing.getInputStream();
		assertEquals(MaxAgeValidationResult.EXPIRES_MISSING, validator.validate(input, now, 0));
	}

	@Test
	public void testLastModifiedMissing() throws Exception {
		Date now = new Date();
		InputStream input = easyListLastModifiedMissing.getInputStream();
		assertEquals(MaxAgeValidationResult.LAST_MODIFIED_MISSING, validator.validate(input, now, 0));
	}

	private Date getDate(String date) throws ParseException {
		return new SimpleDateFormat("dd MMM yyyy HH:mm z", Locale.US).parse(date);
	}
}
