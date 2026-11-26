# 0004. Git branch names, tags and rollback

- **Status:** Accepted
- **Date:** 2026-11-27
- **Ticket:** 1

## Context

Each piece of implementation work gets its own branch, is merged into `main` with `--no-ff`, and the branch is **never deleted** afterwards. The owner keeps branches as reference points: they show which commits made up a piece of work, and make it easy to roll that work back or apply it again.

Kept branches pile up over time. Without conventions it becomes hard to tell what a branch was for, which commit finished a ticket, and how to undo a merge safely.

## Decision

### 1. Branch names

- New branches are named `<type>/<short-topic>`, for example `feature/provider-interface`, `fix/retention-null`.
- Types: `feature/`, `fix/`, `refactor/`, `chore/`, and `docs/` for a documentation change that needs a branch.
- ADRs are not written on branches; they are committed directly on `main`.
- Existing branches keep their names (`data-visualization`, `kotlin-setup-nullable`, `layered-packages`, `environment-profiles`). Renaming would mean deleting them.

### 2. Merging

- Branches are merged into `main` with `git merge --no-ff` after all tests pass, so each piece of work has exactly one merge commit.
- Merged branches are never deleted, locally or on GitHub. Deleting any branch needs the owner's approval.

### 3. Tags

Annotated tags mark points in `main` that matter. Tags are never moved or deleted.

| Tag | Placed on | Example |
|---|---|---|
| `adr-<nnnn>` | The merge commit that implements an ADR | `adr-0001` = `cbf7b86`, `adr-0003` = `a68d58c`, `adr-0002` = `c92a4d2` |
| `ticket-<n>-done` | The commit on `main` that completes a ticket, once the owner confirms it is done | `ticket-1-done` |

ADRs that need no code (such as this one) get no `adr-` tag.

### 4. Rolling back

- To undo a piece of work on `main`, revert its merge commit: `git revert -m 1 <merge-commit>`. History is kept; nothing is rewritten.
- **To re-apply the work later, revert the revert commit** (or cherry-pick the branch's commits). Merging the same branch again does nothing, because git considers it already merged.
- The kept branch shows exactly which commits belonged to the work.

### 5. If the branch list gets long

When there are so many branches that the list is hard to read (roughly 20 or more), old merged branches may be turned into tags named `archive/<branch>`, keeping every commit reachable. This happens only with the owner's approval.

## Alternatives considered

- **Delete branches after merging:** rejected by the owner. The merge commit keeps the history, but the branch name is the easiest way to find a piece of work.
- **Squash merges:** rejected. A single squashed commit hides the steps of the work, and the branch would no longer match what is in `main`.
- **Tags only, no kept branches:** rejected. Tags mark points in time; branches show the whole piece of work.

## Consequences

- The branch list grows with every piece of work; the `<type>/` prefixes keep it readable.
- Any ADR's implementation or any finished ticket can be found by tag.
- Rolling back is always a new commit, so shared history never changes.
- An old branch must not be reused for new work; new work starts a new branch from `main`.
