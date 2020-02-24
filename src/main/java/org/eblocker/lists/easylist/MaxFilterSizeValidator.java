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
import java.io.FileReader;
import java.io.IOException;

public class MaxFilterSizeValidator {

    private MaxFilterSizeValidator() {
    }

    public static void verifyFilterSize(EasyListDescription list) throws IOException {
        BufferedReader reader;
        FileReader fileReader = new FileReader(list.getFilename());
        reader = new BufferedReader(fileReader);
        String line;

        int lines = 0;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("!")) {
                lines++;
            }
        }

        reader.close();
        fileReader.close();

        if (lines > list.getMaxFilterSize()) {
            throw new IOException(String.format("Maximum filter sized exceeded (filter size %d > max size %d)", lines, list.getMaxFilterSize()));
        }
    }
}