# Contributing

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<scope>): <imperative summary>
```

Allowed types are `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, and `security`.
Keep the subject concise and explain breaking changes in the body when needed.

Examples:

- `feat(web): add topic filtering`
- `fix(android): restore cached messages offline`
- `security(config): remove deployment credentials from examples`

## Release versions

`VERSION` is the single source of truth for the site and Android app version. Update it for every user-facing site or app release, then build and tag the same semantic version as `v<version>`.

## Public repository checks

Before committing or pushing, run `pwsh -File scripts/audit-public.ps1`. Do not commit `.env` files, local Gradle properties, signing keys, database files, screenshots containing private data, deployment logs, or real hostnames, usernames, tokens, passwords, or access keys.
