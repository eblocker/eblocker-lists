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

import org.eblocker.lists.tools.ResourceInputStream;
import org.eblocker.lists.util.HttpClient;
import org.eblocker.lists.util.HttpClientFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Date;
import java.util.Properties;

public class DownloadLists {

	public static void main(String[] args) throws Exception {
		HttpClient httpClient = HttpClientFactory.create();

		Properties properties = new Properties();
		properties.load(ResourceInputStream.get("lists.properties"));

		EasyListConfiguration configuration = new EasyListConfiguration(properties);
		if (! new File(configuration.getDirectory()).isDirectory()) {
			throw new RuntimeException("Directory " + configuration.getDirectory() + " does not exist or is not a directory");
		}
		
		for (EasyListDescription list : configuration.getLists()) {
			String filename = list.getFilename();
			String url = list.getURL();
			System.out.println("Downloading: " + url + " -> " + filename);
			downloadList(httpClient, url, filename);
			verifyListChecksum(filename);
			verifyListAge(filename);
			MaxFilterSizeValidator.verifyFilterSize(list);
			EasyListSyntaxValidator.verifyAndRepair(list);
		}
	}

	private static void verifyListChecksum(String filename) throws IOException, NoSuchAlgorithmException {
		ChecksumValidator validator = new ChecksumValidator();
		FileInputStream input = new FileInputStream(filename);
		ChecksumValidationResult validationResult = validator.validate(input);
		if (validationResult != ChecksumValidationResult.OK) {
			throw new IOException("Could not validate checksum of " + filename + ". Expected result OK, but got: " + validationResult);
		}
	}
	
	private static void verifyListAge(String filename) throws IOException, ParseException {
		MaxAgeValidator validator = new MaxAgeValidator();
		FileInputStream input = new FileInputStream(filename);
		if (validator.validate(input, new Date()) != MaxAgeValidationResult.OK) {
			throw new RuntimeException("Could not validate age of " + filename);
		}		
	}

	private static void downloadList(HttpClient client, String url, String filename) throws IOException {
		try (InputStream input = client.download(url, "", "")) {
			Files.copy(input, Paths.get(filename), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
