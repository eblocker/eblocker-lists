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

import java.util.List;
import java.util.Objects;

public class Scriptlet {
    private final List<String> names;
    private final String code;

    public Scriptlet(List<String> names, String code) {
        this.names = names;
        this.code = code;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (String name: names) {
            builder.append("/// ");
            if (builder.length() > 4) {
                builder.append("alias ");
            }
            builder.append(name);
            builder.append("\n");
        }
        builder.append(code);
        builder.append("\n");
        return builder.toString();
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Scriptlet scriptlet = (Scriptlet) o;
        return Objects.equals(names, scriptlet.names) &&
            Objects.equals(code, scriptlet.code);
    }

    @Override public int hashCode() {
        return Objects.hash(names, code);
    }

    public List<String> getNames() {
        return names;
    }

    public String getCode() {
        return code;
    }
}
