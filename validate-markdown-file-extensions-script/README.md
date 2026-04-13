# Set Up Workflow for Validating Markdown File Extensions

This workflow validates that Markdown files in Docusaurus projects use the correct file extensions:

- Files in documentation locale folders (default: `docs/en-us/`, `docs/ja-jp/`) must use `.mdx` extension
- Files in components directories (default: `src/components/`) must use `.mdx` extension

## Why this matters

Docusaurus requires `.mdx` extensions for files that may contain JSX components or need advanced MDX features. Inconsistent extensions can cause build failures and deployment issues.

## Set up workflow

The following instructions will guide you through setting up the workflow in your repository to ensure that all Markdown files have the correct extensions.

### Step 1: Add the workflow file to your repository

Copy the [**validate-markdown-file-extensions.yaml**](validate-markdown-file-extensions.yaml) file to your repository's `.github/workflows/` directory.

> [!TIP]
>
> If your project structure differs from the defaults, you can customize the paths by uncommenting and modifying the `with` section:
>
> ```yaml
> with:
>   docs-paths: "your/docs/path/,another/docs/path/"
>   components-path: "your/components/path/"

### Step 2: Configure branch protection

1. Navigate to your repository on GitHub.
1. Select the **Settings** tab (requires admin access).
1. Select the **Rulesets** in the left sidebar.
1. Select **Add rule** or edit an existing rule.
1. Under **Target branches**, add the necessary branches (for example, **main**, **3.\***, and **4.\***).
1. Enable **Require status checks to pass**.

### Step 3: Add the workflow as a required check

1. In the search box under **Status checks that are required**, type **Check for .md files in docs locale folders and components**.
1. Select it when it appears (it will show up after the first PR runs the workflow).
1. Enable **"Do not allow bypassing the above settings" (prevents admin override)**.
1. Select **Create** or **Save changes**.
