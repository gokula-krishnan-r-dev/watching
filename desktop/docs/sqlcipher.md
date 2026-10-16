# SQLCipher licensing decision (d1)

**Decision:** Use **SQLCipher Community Edition** (BSD-style) via

`rusqlite` feature `bundled-sqlcipher-vendored-openssl`.

| Option | Notes |
| --- | --- |
| Community (chosen) | Suitable for open distribution; OpenSSL-linked; no Zetetic commercial license required for Community APIs we use |
| Commercial SQLCipher | Only if we later need Zetetic support contracts / FIPS packaging — revisit before enterprise SKU |

Production builds always open the DB with a key from the OS secret store (`SecretStore` / `accounts::DB_KEY`). Wrong keys fail closed.
