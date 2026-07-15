# Publishing `ai.hiapi:hiapi` to Maven Central

The build is already wired for Central. Releasing is a one-time setup
(account + namespace + GPG key) followed by a single `mvn deploy` per version.

The `release` profile in `pom.xml` builds the source jar, javadoc jar,
GPG-signs every artifact, and uploads to the **Sonatype Central Portal** via
`central-publishing-maven-plugin` (0.11.0 — older 0.7.x throws a spurious
`Unrecognized field "warnings"` Jackson error *after* a successful upload)
(`central.sonatype.com` — the current system that replaced OSSRH/`oss.sonatype.org`).

---

## One-time setup

### 1. Central Portal account

1. Go to <https://central.sonatype.com> and **Sign in** (GitHub or Google login works).
2. That's the account — no separate JIRA ticket like the old OSSRH flow.

### 2. Register the `ai.hiapi` namespace (DNS-verified)

1. Central Portal → **View Namespaces** → **Add Namespace**.
2. Enter `ai.hiapi`.
3. The Portal shows a **verification key** (a random string). Because this is a
   domain namespace (`ai.hiapi` ⇒ the domain `hiapi.ai`), verify it by **DNS**:
   add a **TXT record** to `hiapi.ai` whose value is that verification key.
   - Host/name: `hiapi.ai` (the apex), record type `TXT`, value = the key.
4. Back in the Portal, click **Verify Namespace**. Once DNS propagates
   (minutes to an hour), the namespace flips to *verified* and you can delete
   the TXT record if you like (keeping it is fine too).

> Alternative if DNS is a hassle: register `io.github.hiapiai` instead (verified
> by GitHub org ownership, no DNS). But that requires renaming every Java package
> from `ai.hiapi` to `io.github.hiapiai` and changing the Maven coordinates — not
> worth it when we own `hiapi.ai`.

### 3. Generate a publishing token

1. Central Portal → your account (top right) → **Generate User Token**.
2. It returns a **username** and **password** pair (these are NOT your login
   credentials — they're a scoped token). Copy both.

### 4. Create a GPG signing key

Central requires every artifact to be GPG-signed, and the public key must be
discoverable on a public keyserver.

```bash
# Generate a key (RSA 4096, no expiry is fine for a project key).
gpg --full-generate-key
#   Real name: HiAPI
#   Email:     support@hiapi.ai     (match the <developer><email> in pom.xml)
#   Set a passphrase — you'll need it at deploy time.

# Find the key's long id:
gpg --list-secret-keys --keyid-format=long
#   sec   rsa4096/ABCD1234EF567890 2026-06-29 ...   <-- the part after the slash

# Publish the PUBLIC key so Central can verify signatures:
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EF567890
gpg --keyserver keys.openpgp.org     --send-keys ABCD1234EF567890
```

### 5. Wire credentials into `~/.m2/settings.xml`

Create `~/.m2/settings.xml` (there is none yet on this machine) with the token
from step 3. A template is checked in at `settings.xml.template` next to this file.

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    <server>
      <id>central</id>            <!-- must match publishingServerId in pom.xml -->
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

---

## Release a version

From `hiapi-fullstack/hiapiai/sdk/hiapi-java`:

```bash
# Sanity check first — normal build + 50 tests, no GPG needed:
mvn clean test

# Release: builds sources + javadoc, signs, uploads to the Portal.
# Pass the GPG passphrase from step 4 (loopback pinentry is already configured).
mvn -Prelease clean deploy -Dgpg.passphrase='YOUR_GPG_PASSPHRASE'
```

What happens:

1. Maven builds `hiapi-0.2.1.jar`, `hiapi-0.2.1-sources.jar`,
   `hiapi-0.2.1-javadoc.jar`, plus a `.asc` signature and checksums for each.
2. The `central-publishing-maven-plugin` uploads the bundle and **waits until
   validation passes** (`waitUntil=validated`).
3. Because `autoPublish=true` (flipped after the manually-reviewed first release),
   it then **publishes automatically** — no browser step needed. It syncs to
   Maven Central: searchable on `central.sonatype.com` quickly, and resolvable
   from `repo1.maven.org` / appearing on `search.maven.org` within ~15–30 min.

To go back to reviewed releases, set `<autoPublish>false</autoPublish>` — the
upload then stops as a draft in Central Portal → **Deployments**, where you
review and click **Publish** manually.

---

## After the first publish

- **Coordinates are immutable.** You can never re-upload `0.2.1`; the next fix
  must be `0.2.2`. Bump `<version>` in `pom.xml` (and the README install snippet)
  for every release.
- Verify the published artifact resolves:
  ```bash
  mvn dependency:get -Dartifact=ai.hiapi:hiapi:0.2.1
  ```

## Pre-flight checklist

- [ ] `support@hiapi.ai` in `pom.xml` `<developers>` is a real inbox (or changed).
- [ ] GitHub repo `HiAPIAI/hiapi-java` exists (the `<scm>` URL points at it).
- [ ] Namespace `ai.hiapi` is *verified* in the Central Portal.
- [ ] GPG public key pushed to a keyserver.
- [ ] `~/.m2/settings.xml` has the `central` server token.
- [ ] `mvn clean test` is green.
- [ ] Any stale earlier-version drafts in the Portal **Deployments** tab are
      **Dropped** first — an older `0.1.0` upload from an aborted run must not
      linger; only the `0.2.1` deployment should remain before you Publish.
