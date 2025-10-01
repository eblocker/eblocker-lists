#!/usr/bin/env python3
#
# parse given json trusted apps list and prints out their domains
#
import sys
import re
import json

if len(sys.argv) != 2:
    exit(f'Usage: {sys.argv[0]} <json file>')

jsonfile = sys.argv[1]

def extract_addresses(txt):
    domains = []
    ips = []
    for line in txt.splitlines():
        addr = line.strip()
        m = ip_pattern.match(addr)
        if m:
            ips.append(addr)
        else:
            m = fqdn_pattern.match(addr)
            if m:
                domains.append(addr.lower())
            else:
                break # ignore trailing garbage (e.g. email signatures)
    return domains, ips

def normalize_description(desc):
    for lang in ['de', 'en']:
        if lang in desc and not desc[lang].endswith(community_tag):
            desc[lang] += ' ' + community_tag

def check_apps(current_apps):
    for app in current_apps:
        for entry in current_apps:
            if entry['name']==app['name']:
                for domain in app['whitelistedDomains']:
                    print(f"trusted app '{entry['name']}': domain '{domain}'")

# read file
with open(jsonfile, 'r') as myfile:
    data=myfile.read()
currentapps = json.loads(data)

check_apps(currentapps)
