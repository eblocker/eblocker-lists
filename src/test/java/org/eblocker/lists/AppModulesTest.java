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
package org.eblocker.lists;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.eblocker.lists.easylist.FileResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;

public class AppModulesTest {
    JsonNode rootNode;
    
    @Before
    public void setup() throws IOException {
        FileResource resource = new FileResource("lists/appModules.json");
        InputStream is = resource.getInputStream();

        // checks if application modules list is actually valid json
        ObjectMapper mapper = new ObjectMapper();
        rootNode = mapper.readTree(is);
    }

    @Test
    public void testParsing() {
        // simple sanity checks:
        // we're expecting a list of modules
        Assert.assertEquals(JsonNodeType.ARRAY, rootNode.getNodeType());

        // each module must contain id and name
        rootNode.forEach(node -> {
            Assert.assertEquals(JsonNodeType.OBJECT, node.getNodeType());
            Assert.assertNotNull(node.get("id"));
            Assert.assertNotNull(node.get("name"));
        });
    }
    
    @Test
    public void testUniqueIDs() {
        Set<Integer> ids = new HashSet<>();
        rootNode.forEach(node -> {
            JsonNode idNode = node.get("id");
            Assert.assertTrue(idNode.canConvertToInt());
            int id = idNode.intValue();
            Assert.assertFalse("ID " + id + " is not unique!", ids.contains(id));
            ids.add(id);
        });
    }

    @Test
    public void testUniqueNames() {
        Set<String> names = new HashSet<>();
        rootNode.forEach(node -> {
            JsonNode nameNode = node.get("name");
            String name = nameNode.asText();
            if (! name.equals("leer")) {
                Assert.assertFalse("Name '" + name + "' is not unique!", names.contains(name));
            }
            names.add(name);
        });
    }

    @Test
    public void testReservedIDs() {
        rootNode.forEach(node -> {
            JsonNode idNode = node.get("id");
            int id = idNode.intValue();
            Assert.assertNotEquals("ID 9998 is reserved for the SSL exemption list app module", 9998, idNode.intValue());
            Assert.assertTrue("IDs 10000 and up are reserved for user-defined app modules", id < 10000);
        });
    }
}
