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
package org.eblocker.lists.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DomainSet {

    private Node root = new Node("/");

    public void add(Collection<String> domains) {
        domains.forEach(this::insert);
    }

    public void remove(Collection<String> domains) {
        domains.forEach(this::remove);
    }

    public Set<String> getDomains() {
        Set<String> domains = new HashSet<>();
        if (root.children != null) {
            root.children.values().forEach(child -> collectDomains(domains, child, ""));
        }
        return domains;
    }

    public void prettyPrint() {
        if (root.children != null) {
            root.children.values().forEach(child -> prettyPrint(0, child, ""));
        }
    }

    private void prettyPrint(int indent, Node node, String postfix) {
        for(int i = 0; i < indent; ++i) {
            System.out.print(" ");
        }
        if (node.leaf) {
            System.out.print("*");
        } else {
            System.out.print(" ");
        }
        String fullName = "." + node.label + postfix;
        System.out.println(node.label + " (" + fullName + ")");
        if (node.children != null) {
            node.children.values().forEach(child -> prettyPrint(indent + 3, child, fullName));
        }
    }

    private void insert(String domain) {
        String[] labels = getLabels(domain);
        Node node = root;
        for(int i = labels.length - 1; i >= 0; --i) {
            Node child = node.getChildren(labels[i]);
            if (child == null) {
                Node newNode = new Node(labels[i]);
                newNode.leaf = i == 0;
                node.addChild(newNode);
                node = newNode;
            } else if (i == 0) {
                child.children = null;
                child.leaf = true;
            } else if (child.leaf) {
                break;
            } else {
                node = child;
            }
        }
    }

    private void remove(String domain) {
        String[] labels = getLabels(domain);
        List<Node> path = new ArrayList<>();

        // find path to node
        Node node = root;
        for(int i = labels.length - 1; i >= 0; --i) {
            path.add(node);
            Node child = node.getChildren(labels[i]);
            if (child == null) {
                return;
            }
            node = child;
        }

        // delete path backwards until node still has successors
        for(int i = path.size() -1; i >= 0; --i) {
            Node pathNode = path.get(i);
            pathNode.removeChild(node.label);
            if (pathNode.children != null) {
                return;
            }
            node = pathNode;
        }
    }

    private String[] getLabels(String domain) {
        return domain.startsWith(".") ? domain.substring(1).split("\\.") : domain.split("\\.");
    }

    private void collectDomains(Set<String> domains, Node node, String postfix) {
        String fullName = "." + node.label + postfix;
        if (node.children == null) {
            domains.add(fullName);
        } else {
            node.children.values().forEach(child -> collectDomains(domains, child, fullName));
        }
    }

    private class Node {
        String label;
        boolean leaf;
        Map<String, Node> children;

        public Node(String label) {
            this.label = label;
        }

        public void addChild(Node node) {
            if (children == null) {
                children = new HashMap<>();
            }
            children.put(node.label, node);
        }

        public Node getChildren(String label) {
            if (children == null) {
                return null;
            }
            return children.get(label);
        }

        public void removeChild(String label) {
            children.remove(label);
            if (children.isEmpty()) {
                children = null;
            }
        }
    }
}