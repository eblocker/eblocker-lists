/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.lists.scriptlets;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScriptletsParserTest {
    @Test
    public void test() {
        ScriptletsParser parser = new ScriptletsParser();
        List<Scriptlet> result = parser.parse(Stream.of(
            "prefix",
            "",
            "/// name1.js",
            "/// alias name2.js",
            "(function() {",
            "    some code...",
            "})();",
            "", "",
            "// comment1",
            "/// nameX.js",
            "// comment2",
            "(function() {",
            "    some other code...",
            "})();",
            "", "",
            "postfix"
        )).collect(Collectors.toList());

        List<Scriptlet> expected = List.of(
            new Scriptlet(List.of("name1.js", "name2.js"), "(function() {\n    some code...\n})();"),
            new Scriptlet(List.of("nameX.js"), "// comment2\n(function() {\n    some other code...\n})();")
        );
        Assert.assertEquals(expected, result);
    }
}
