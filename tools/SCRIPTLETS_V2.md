# Scriptlets 2.0 development build

The legacy `ScriptletsDownloader` still consumes the pinned single-file uBlock resource. This separate Maven profile builds modern, standalone page-world scriptlets from uBlock's registered ES modules. It does not change `scriptlets.properties` or the default list update.

Requirements: Node.js 22 or newer and a local checkout of `gorhill/uBlock` at commit `765c51daa13143b5a7cbf60be4633fb8e7d324a5`. The checkout must also contain the legacy commit `e93117cbb607472a830e1c0653dfbddde4c965fc` as a Git object. The profile checks the current commit and fails if it differs.

```sh
mvn clean -Pscriptlets-v2 -Dublock.source=/absolute/path/to/uBlock package
```

This command builds a development package containing the scriptlets. For an installable lists package, also run the normal feed update profile so the package contains current filter lists and certificates:

```sh
mvn clean -Pupdate-lists,scriptlets-v2 -Dublock.source=/absolute/path/to/uBlock package
```

Check the generated `lists/` and `lists_src/` contents and the Debian package payload before installation. A scriptlet-only package must not replace an installed full lists package.

The package copies generated `.js` files, `manifest.json`, and `LICENSE-UBLOCK.txt` from `target/scriptlets-v2`. The manifest records the upstream commits, generated and omitted names, and legacy fallbacks. The build currently emits 80 modern names and aliases plus 16 legacy names that no longer exist in the module registry. It skips scriptlets marked as trusted and scriptlets intended for uBlock's isolated extension world. Generated JavaScript remains under uBlock Origin's GPL-3.0 license; review distribution obligations before publishing a package.

The matching eBlocker ICAP changes recognize the `/* eBlocker scriptlet v2;` marker, JSON-encode arguments, and inject v2 code at the start of the HTML head. A per-response nonce is added to the early scripts and to a restrictive CSP header. The existing legacy scriptlet flow remains available. HTTPS inspection still determines whether eBlocker can modify a particular site's HTML.
