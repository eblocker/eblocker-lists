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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaxAgeValidator {
	private final Pattern lastModifiedPattern = Pattern.compile("! Last modified: (.*)");
	private final Pattern expiresPattern = Pattern.compile("! Expires: (\\d+) (days|hours).*");
	private final DateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm z", Locale.US);
	 
	/**
	 * Checks whether the given EasyList is still valid at the given date
	 * @param listStream the stream to read the EasyList from. As soon as the comments "Last modified"
	 * and "Expires" are found, reading from the stream is stopped
	 * @param now
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
	public MaxAgeValidationResult validate(InputStream listStream, Date now) throws IOException, ParseException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(listStream, StandardCharsets.UTF_8));

		Duration maxAge = null;
		Date lastModified = null;
		
		String line = null;
		while (null != (line = reader.readLine())) {
			Matcher matcher = lastModifiedPattern.matcher(line);
			if (matcher.matches()) {
				lastModified = dateFormat.parse(matcher.group(1));
			}
			
			matcher = expiresPattern.matcher(line);
			if (matcher.matches()) {
				long number = Long.parseLong(matcher.group(1));
				if ("days".equals(matcher.group(2))) {
					maxAge = Duration.ofDays(number);
				} else if ("hours".equals(matcher.group(2))) {
					maxAge = Duration.ofHours(number);
				} else {
					throw new RuntimeException("Why did the expiresPattern not match " + matcher.group(2) + "?");
				}
			}
			if (maxAge != null && lastModified != null) {
				return getValidationResult(maxAge, lastModified, now);
			}
		}
		
		if (lastModified == null) {
			return MaxAgeValidationResult.LAST_MODIFIED_MISSING;
		} else if (maxAge == null) {
			return MaxAgeValidationResult.EXPIRES_MISSING;
		} else {
			throw new RuntimeException("This should never happen. Either lastModied or maxAge should be null.");
		}
	}


	private MaxAgeValidationResult getValidationResult(Duration maxAge,	Date lastModified, Date now) {
		Instant expirationDate = lastModified.toInstant().plus(maxAge);
		if (now.toInstant().isBefore(expirationDate)) {
			return MaxAgeValidationResult.OK;
		} else {
			return MaxAgeValidationResult.EXPIRED;
		}
	}
}
