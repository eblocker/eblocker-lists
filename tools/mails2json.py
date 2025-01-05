#!/usr/bin/env python3
#
# Splits emails saved by macOS Mail and extracts an app suggestion for each mail.
#
# If multiple mails are selected in macOS Mail and saved as plain text they are
# joined by form-feed characters.
#
# A suggested app is extracted from each mail and converted to JSON.
#
# If an existing app is extended, its app ID is set. Otherwise the ID is set to "-1".
#
import sys
import re
import json

if len(sys.argv) != 3:
    exit(f'Usage: {sys.argv[0]} <json file> <mails file>')

jsonfile = sys.argv[1]
infile = sys.argv[2]

existing_app_pattern = re.compile(r'Suggestion for existing app:.*\(ID (\d+)\)')
description_pattern = re.compile(r'Description:\s*\nde:([^\n]*).*?\nen:([^\n]*)', re.DOTALL)
name_pattern = re.compile(r'Name:\s*(.*)')
domains_pattern = re.compile(r'Domains:\s*((\n\S+ *)+)')
ip_pattern = re.compile(r'\d{1,3}(\.\d{1,3}){3}')
fqdn_pattern = re.compile(r'(PART(\.PART)+)'.replace('PART', r'([a-zA-Z0-9\-]+)'))
community_tag = '(#eBlocker community)'

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

def parse_app(txt):
    app = {}

    # Get name:
    m = name_pattern.search(txt)
    if m:
        app['name'] = m.group(1).strip()
    else:
        raise Exception(f'Could not extract name from mail text:\n{txt}')

    # Get description:
    m = description_pattern.search(txt)
    if m:
        app['description'] = {'de': m.group(1).strip(), 'en': m.group(2).strip()}
    else:
        raise Exception(f'Could not extract description from mail text:\n{txt}')

    # Get domains and IPs:
    m = domains_pattern.search(txt)
    if m:
        domains, ips = extract_addresses(m.group(1).strip())
        app['whitelistedDomains'] = domains
        app['whitelistedIPs'] = ips
    else:
        raise Exception(f'Could not extract domains/IPs from mail text:\n{txt}')

    # Existing app?
    m = existing_app_pattern.search(txt)
    if m:
        app['id'] = int(m.group(1))
    else:
        app['id'] = -1

    normalize_description(app['description'])

    return app

def parse_mails(infile):
    apps = []
    with open(infile) as f:
        txt = f.read()
        mails = txt.split('\f') # macOS Mail saves mails as plain text separated by form-feeds
        for mail in mails:
            mail = '\n'.join(mail.splitlines()) # normalize newline characters
            apps.append(parse_app(mail))
    return apps

def merge_apps(current_apps,new_apps):
    nextId = 0
    for entry in current_apps:
        currId = int(entry['id'])
        if currId > nextId and currId < 9999:
            nextId = currId
    nextId += 1

    for app in new_apps:
        newApp = True
        for entry in current_apps:
            if entry['name']==app['name']:
                newApp = False
                for domain in app['whitelistedDomains']:
                    found = False
                    for listed in entry['whitelistedDomains']:
                        if listed in domain:
                            found = True
                    if not found:
                        entry['whitelistedDomains'].append(domain)
                        print("trusted app",entry['name'],": added domain",domain)

        if newApp:
            app['id'] = nextId
            current_apps.append(app)
            print("trusted app",app['name'],"added.")
            nextId += 1

# read file
with open(jsonfile, 'r') as myfile:
    data=myfile.read()
currentapps = json.loads(data)
newapps = parse_mails(infile)

merge_apps(currentapps,newapps)

with open(jsonfile, 'w') as myfile:
    myfile.write(json.dumps(currentapps, ensure_ascii=False, indent=2))
