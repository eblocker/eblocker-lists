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

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.easylist.EasyListLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EasyListSyntaxValidator {

    private static final Logger LOG = LoggerFactory.getLogger(EasyListSyntaxValidator.class);

    private static final Pattern OPTIONS_PATTERN = Pattern.compile("~?[a-zA-Z0-9_-]+(=[^,]+)?(,~?[a-zA-Z0-9_-]+(=[^,]+)?)*");

    private static final String CHECKSUM_PREFIX = "! Checksum: ";
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile(CHECKSUM_PREFIX + "(\\S+)");

    private EasyListSyntaxValidator() {
    }

    public static void verifyAndRepair(EasyListDescription list) throws IOException {

        Path src = Paths.get(list.getFilename());
        Path tmp = Files.createTempFile("easylist.", ".tmp");
        boolean appliedFix = false;

        try (BufferedReader reader = Files.newBufferedReader(src);
             BufferedWriter writer = Files.newBufferedWriter(tmp)) {
            String line;

            while ((line = reader.readLine()) != null) {
                // Hotfix for global $ping rule matching all URLs
                if (line.equals("$ping")) {
                    LOG.info("Removing global '$ping' rule");
                    appliedFix = true;
                    continue;
                }

                // Parse rule to identify rules we are interested in. E.g. we skip CSS hiding rules.
                if (line.startsWith("!") || new EasyListLineParser().parseLine(line) == null) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                // Repair rule with ambiguous dollar in pattern
                int dollar = line.lastIndexOf('$');
                if (dollar >= 0) {
                    String potentialOptions = line.substring(dollar+1);
                    if (!OPTIONS_PATTERN.matcher(potentialOptions).matches()) {
                        LOG.info("Found rule with ambiguous dollar sign in rule: [{}] - masking the dollar with dummy option", line);
                        line = line + "$dummy";
                        appliedFix = true;
                    }
                }
                writer.write(line);
                writer.newLine();

                // Parse line again
                Filter filter = new EasyListLineParser().parseLine(line);

                // Run test set against this filter
                for (EasyListRuleTest easyListRuleTest: list.getEasyListRuleTests()) {
                    Decision decision = filter.filter(easyListRuleTest).getDecision();
                    if (!easyListRuleTest.isAllowed(decision)) {
                        String msg = "Unexpected filter result " + decision + " by filter [" + line + "] for test " + easyListRuleTest;
                        LOG.error(msg);
                        throw new IOException(msg);
                    }
                }
            }
        }

        if (appliedFix) {
            Path tmp2 = recalculateChecksum(tmp);
            Files.move(tmp2, src, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    /**
     * Reads an EasyList from a file and writes a temporary file with the correct checksum
     * @param source
     * @return
     */
    private static Path recalculateChecksum(Path source) throws IOException {
        Path tmp = Files.createTempFile("easylist.recalculated.checksum.", ".tmp");
        ChecksumValidator checksumValidator = new ChecksumValidator();
        Checksums checksums;
        try {
            checksums = checksumValidator.getChecksums(Files.newInputStream(source));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not get checksum from file " + source, e);
        }
        try (BufferedReader reader = Files.newBufferedReader(source);
             BufferedWriter writer = Files.newBufferedWriter(tmp)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = CHECKSUM_PATTERN.matcher(line);
                if (matcher.matches()) {
                    line = CHECKSUM_PREFIX + checksums.getCalculatedDigest();
                }
                writer.write(line);
                writer.newLine();
            }
        }
        return tmp;
    }
}
