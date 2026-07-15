# Local Android signing key

The release keystore and `keystore.properties` in this directory are intentionally ignored by Git. Keep both files in the local project directory and back them up securely. Losing the keystore means future Android updates cannot be installed as upgrades over this release.

The properties file must contain:

```properties
storeFile=keystore/ntfy-release.jks
storePassword=replace-with-store-password
keyAlias=ntfy-release
keyPassword=replace-with-key-password
```
