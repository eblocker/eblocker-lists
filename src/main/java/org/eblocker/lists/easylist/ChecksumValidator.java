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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the checksum of a list in EasyList format.
 * 
 * See also: https://adblockplus.org/en/filters, section "Comments", "Special comments"
 */
public class ChecksumValidator {

	private final Pattern checksumPattern = Pattern.compile("! Checksum: (\\S+)");
	private final byte[] newline = "\n".getBytes(StandardCharsets.UTF_8);

	/**
	 * Reads a list in EasyList format from the given input stream, finds the checksum
	 * and verifies it.
	 * 
	 * KNOWN BUGS: if there is a newline at the end of the file, this method will probably fail
	 * (if I understand the description at https://adblockplus.org/en/filters correctly).
	 * The problem is that BufferedReader does not tell us if the last line was terminated with
	 * a newline character. A possible solution would be to clone the MessageDigest and try
	 * to verify the checksum again with: byte[] md5 = clonedDigest.digest(newline)
	 * 
	 * @param listStream
	 * @return the result of the validation
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public ChecksumValidationResult validate(InputStream listStream) throws NoSuchAlgorithmException, IOException {
        Checksums checksums = getChecksums(listStream);
        return checksums.getValidationResult();
	}

    public Checksums getChecksums(InputStream listStream) throws NoSuchAlgorithmException, IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(listStream, StandardCharsets.UTF_8));

        MessageDigest digest = MessageDigest.getInstance("MD5");
        Checksums checksums = new Checksums();
        String line = null;
        boolean firstLine = true;
        while(null != (line = reader.readLine())) {
            Matcher matcher = checksumPattern.matcher(line);
            if (matcher.matches()) {
                checksums.setExpectedDigest(matcher.group(1));
            } else {
                if (! line.isEmpty()) {
                    // a normal line:
                    if (! firstLine) {
                        digest.update(newline);
                    }
                    firstLine = false;
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
        }


        byte[] md5 = digest.digest();

        // Encode MD5 to Base64 and remove trailing = characters
        String base64 = Base64.getEncoder().encodeToString(md5);
        checksums.setCalculatedDigest(base64.replace("=", ""));
        return checksums;
    }

}
