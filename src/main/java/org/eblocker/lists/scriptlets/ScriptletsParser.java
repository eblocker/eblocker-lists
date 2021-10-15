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

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Parses a scriptlets resource (as a stream of lines) into single scriptlets.
 * Each scriptlet can have multiple names.
 */
public class ScriptletsParser {
    public Stream<Scriptlet> parse(Stream<String> lines) {
        return StreamSupport.stream(new ScriptletSpliterator(lines), false);
    }

    private class ScriptletSpliterator implements Spliterator<Scriptlet>, Consumer<String> {
        private final Spliterator<String> source;
        private final Pattern NAME_OR_ALIAS = Pattern.compile("/// (alias )?([a-z0-9\\-_\\.]+)", Pattern.CASE_INSENSITIVE);
        private final String END_SCRIPTLET = "})();";
        private boolean inScriptlet = false;
        private List<String> names;
        private StringBuilder code;

        public ScriptletSpliterator(Stream<String> lines) {
            this.source = lines.spliterator();
            prepareNextScriptlet();
        }

        @Override public boolean tryAdvance(Consumer<? super Scriptlet> action) {
            while (source.tryAdvance(this)) {
                if (scriptletEnded()) {
                    action.accept(new Scriptlet(names, code.toString()));
                    prepareNextScriptlet();
                    return true;
                }
            }
            if (scriptletEnded()) {
                // emit the last scriptlet
                action.accept(new Scriptlet(names, code.toString()));
                return false;
            }
            return false;
        }

        private void prepareNextScriptlet() {
            code = new StringBuilder();
            names = new ArrayList<>();
        }

        private boolean scriptletEnded() {
            return !inScriptlet && names.size() > 0;
        }

        @Override public void accept(String string) {
            Matcher m = NAME_OR_ALIAS.matcher(string);
            if (m.matches()) {
                String name = m.group(2);
                if (!name.endsWith(".js")) {
                    name = name.concat(".js");
                }
                names.add(name);
                inScriptlet = true;
            } else if (string.equals(END_SCRIPTLET)) {
                if (inScriptlet) {
                    code.append("\n");
                    code.append(string);
                }
                inScriptlet = false;
            } else if (inScriptlet) {
                if (code.length() > 0) {
                    code.append("\n");
                }
                code.append(string);
            }
        }

        @Override public Spliterator trySplit() {
            return null;
        }

        @Override public long estimateSize() {
            return source.estimateSize();
        }

        @Override public int characteristics() {
            return source.characteristics();
        }
    }
}
