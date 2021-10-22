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

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MaxFilterSizeValidatorTest {
    private static final String EASYLIST_FILE = "src/test/resources/easyprivacy_general.txt";

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void testSizeExceeded() throws IOException {
        expectedEx.expect(IOException.class);
        expectedEx.expectMessage("Maximum filter sized exceeded (filter size 18 > max size 10)");
        EasyListDescription list = new EasyListDescription("", EASYLIST_FILE, 0, 999, 10, true, null);
        MaxFilterSizeValidator.verifyFilterSize(list);
    }

    @Test
    public void testSizeOK() throws IOException {
        EasyListDescription list = new EasyListDescription("", EASYLIST_FILE, 0, 999, 100, true, null);
        MaxFilterSizeValidator.verifyFilterSize(list);
    }
}
