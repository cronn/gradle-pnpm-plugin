# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Plugin Development

- Use auto-discovery to enable features when possible (e.g. checking for tool-specific configuration files)
- Add debug logging whenever deriving configuration from the environment or configuration files
- Extensions must be compatible with the Gradle configuration cache

## Conventions

- Use Conventional Commits
- Commit unrelated changes individually and keep commits small

## Documentation

- Describe user-facing changes in a changeset (`@changesets/cli`)
- Keep user-facing documentation short and concise without implementation details

## GitHub Pull Requests

- Self-assign the PR
- Provide a short paragraph summarizing the introduced changes
- For features, include only changes affecting consumers of the plugin
- Provide a simple example demonstrating the changes
- Reference related GitHub issues closed by the changes
