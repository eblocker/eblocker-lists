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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MaxAgeValidatorTest {
	private MaxAgeValidator validator;

	@Rule
	public FileResource easyPrivacyList = new FileResource("easyprivacy_general.txt");
	
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
		assertEquals(MaxAgeValidationResult.EXPIRED, validator.validate(input, now)); // unless you time-travel to before 25 Jul 2016
	}

	@Test
	public void testValid() throws Exception {
		Date now = new SimpleDateFormat("dd MMM yyyy HH:mm z", Locale.US).parse("23 Jul 2016 9:40 UTC");
		InputStream input = easyPrivacyList.getInputStream();
		assertEquals(MaxAgeValidationResult.OK, validator.validate(input, now));
	}

}
