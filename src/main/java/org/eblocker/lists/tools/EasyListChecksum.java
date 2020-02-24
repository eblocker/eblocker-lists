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
package org.eblocker.lists.tools;

import org.eblocker.lists.easylist.ChecksumValidator;
import org.eblocker.lists.easylist.Checksums;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class EasyListChecksum {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java EasyListChecksum <easylist file>");
            System.exit(1);
        }
        Path path = FileSystems.getDefault().getPath(args[0]);
        ChecksumValidator checksumValidator = new ChecksumValidator();
        Checksums checksums = checksumValidator.getChecksums(Files.newInputStream(path));
        System.out.println("Calculated checksum: " + checksums.getCalculatedDigest());
    }
}
