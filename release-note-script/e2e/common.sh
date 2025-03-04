# Constants
E2E_ROOT=$(pwd)
WORK_DIR=/tmp/rn-e2e-$(date "+%Y%m%d%H%M%S")
PR_DIR=$WORK_DIR/pr
SCRIPT_DIR=$(dirname $E2E_ROOT)/src/main/java
RN_SCRIPT=$SCRIPT_DIR/ReleaseNoteCreation.java
FIXTURE_DIR=$E2E_ROOT/fixture

# Functions
function usage() {
    local PRG=$0
cat <<EOF
Usage: $0 <owner> <repository> <projectPrefix> <version> <PAT>

Example:
    $0 @me scalardb ScalarDB 4.0.0 ghp_xxxx

!!CAUTION!!
This tool creates a project and repo on the GitHub. So please do not use with the product repository and organization.
EOF
    exit 1
}

function createWorkDir() {
    if [ ! -d $WORK_DIR ];then
        mkdir -p $WORK_DIR
    fi
}

function createProject() {
    local owner=$1
    local title=$2

    gh project create --owner "$owner" --title "$title" --format json | jq .number
}

function createRepo() {
    local repo=$1

    gh repo create $repo --private --clone
}

function createLabels() {
    local owner=$1
    local repo=$2
    local labels=$3

    for label in $(echo $labels)
    do
        gh label create "$label" --repo $owner/$repo >/dev/null 2>&1
    done
}

function createMainBranch() {
    echo "test" > README
    git add README
    git commit -m "first commit"
    git branch -M main
    git push -u origin main >/dev/null
}

function createBranchAndCommit() {
    local branch=$1

    git checkout main
    git checkout -b $branch >/dev/null

cat<<EOF > $branch
$branch
EOF

    git add $branch
    git commit -m "$branch"
    git push -u origin $branch >/dev/null
}

function createPRWithReleaseNote() {
    local projectPrefix=$1
    local version=$2
    local branch=$3
    local prText=$4
    local rnText=$5
    local label=$6

    local prDir=$PR_DIR
    local prFile=pr-$branch
    local project="$projectPrefix $version"

cat<<EOF > $prDir/$prFile
## Overview
$prText
## Release notes
$rnText
EOF

    prUri=$(gh pr create --base main --body-file $prDir/$prFile --title "$branch" --project "$project" --label "$label")
    basename $prUri
}

function createPRWithoutReleaseNote() {
    local projectPrefix=$1
    local version=$2
    local branch=$3
    local prText=$4
    local label=$5

    local prDir=$PR_DIR
    local prFile=pr-$branch
    local project="$projectPrefix $version"

cat<<EOF > $prDir/$prFile
## Overview
$prText
EOF

    prUri=$(gh pr create --base main --body-file $prDir/$prFile --title "$branch" --project "$project" --label "$label")
    basename $prUri
}

function mergePullRequest() {
    local prNum=$1
    local owner=$2
    local repo=$3

    gh pr merge $prNum --squash --repo $owner/$repo >/dev/null
}

function deleteRepo() {
    local owner=$1
    local repo=$2

    gh repo delete $owner/$repo --yes >/dev/null
}

function deleteProject() {
    local owner=$1
    local project=$2

    gh project delete "$project" --owner "$owner" >/dev/null
}

function creanUp() {
    local workDir=$1
    local owner=$2
    local repo=$3
    local projNum=$4

    rm -rf $workDir
    deleteRepo $owner $repo
    deleteProject $owner $projNum
}
