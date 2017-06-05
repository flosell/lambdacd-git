#!/bin/bash
set -e

SILENT="true"

test() {
  CMD="lein"

  if [ "${SILENT}" == "true" ]; then
    CMD="${CMD} with-profile dev,silent test"
  else
    CMD="${CMD} test"
  fi

  if [ -z "${LAMBDACD_GIT_TESTREPO_USERNAME}" ] || [ -z "${LAMBDACD_GIT_TESTREPO_PASSWORD}" ]; then
    echo
    echo "================================================================================"
    echo "Could not find LAMBDACD_GIT_TESTREPO_USERNAME AND LAMBDACD_GIT_TESTREPO_PASSWORD"
    echo "SKIPPING end-to-end tests that require authentication to remote repository."
    echo "For Travis CI builds against Pull Requests, this is expected."
    echo
    echo "To run end to end tests against your own repository, set these variables:"
    echo "- LAMBDACD_GIT_TESTREPO_SSH"
    echo "- LAMBDACD_GIT_TESTREPO_HTTPS"
    echo "- LAMBDACD_GIT_TESTREPO_USERNAME"
    echo "- LAMBDACD_GIT_TESTREPO_PASSWORD"
    echo "================================================================================"
    echo

    CMD="${CMD} :skip-e2e-with-auth"
  fi

  ${CMD}
}

push() {
  test && git push
}

release() {
  test && lein release && scripts/github-release.sh
}

function run() {
  if [ -z $1 ]; then
    lein run
  else
    NAMESPACE="lambdacd-git.example.$1-pipeline"
    lein run -m ${NAMESPACE}
  fi
}

if [ $# -ne 0 ] && type $1 &>/dev/null; then
    $1 $2
else
    echo "usage: $0 <goal>

goal:
    test           -- run all tests
    push           -- run all tests and push current state
    run            -- run the simple sample pipeline
    run multi-repo -- run the multi-repo sample pipeline
    release         -- release current version"

    exit 1
fi
