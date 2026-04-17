# Set Up Workflow for Checking PR Project Assignments

The [`.github/workflows/pr-project-assignment-check-reusable.yaml`](../.github/workflows/pr-project-assignment-check-reusable.yaml) workflow checks if a PR has a project assigned in the GitHub sidebar. The following outcomes are possible when the workflow runs:

- **If projects are assigned:** The workflow detects the assigned project(s) and completes without posting a PR comment.
- **If no projects are assigned:** The workflow posts a PR comment mentioning the PR creator (@username) as a heads-up.
  - The workflow also shows a warning annotation (non-blocking) if no projects are found. The reason why this is a warning and not an error is to allow flexibility in cases where project assignment may not be mandatory.

> [!NOTE]
>
> **Backport PRs are automatically excluded:** The workflow will not run at all for backport PRs, avoiding any GitHub Actions costs. A backport PR is identified by a title that starts with "Backport to branch" and includes a branch format like `MAJOR.MINOR-pull-PR_NUMBER` in either the PR title or the branch name (for example, "Backport to branch 3.17-pull-1234" or branch "3.17-pull-1234"). This filtering happens at the workflow trigger level for zero-cost exclusion.

## Requirements

- The repository must have GitHub Projects enabled.
- Workflow requires `contents: read` and `pull-requests: write` permissions.

> [!NOTE]
>
> Supports all GitHub project types:
>
> - Organization and repository-level GitHub Projects V2 (public projects work with default token)
> - User-level GitHub Projects V2 (requires optional token for private projects)  
> - Classic project boards (legacy, being deprecated)

### Private project support

To detect **private projects**, you'll need to provide a Personal Access Token (PAT) with `read:project` scope:

1. **Create a PAT:** Go to [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens)
2. **Add `read:project` scope:** This enables access to private projects.
3. **Add as repository secret:** Store the token as `PROJECT_READ_TOKEN` in your repository secrets.
4. **Uncomment the secrets section** in your workflow file (see example in sample workflow).

> [!WARNING]
>
> **Without this PAT:** Only public projects will be detected. Private projects will be ignored, and the workflow will only return public projects assigned to the PR.

## Implement the workflow

Copy the [`pr-project-assignment-check.yaml`](./pr-project-assignment-check.yaml) file to your repository's `.github/workflows/` directory to automatically check project assignments when PRs are opened.

> [!NOTE]
>
> The [`check_pr_project_assignment`](./check_pr_project_assignment) script queries GitHub's GraphQL API to check for project assignments. You don't need to include it in your repository, unless you want to customize the script for your specific needs.
