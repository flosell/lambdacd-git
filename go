#!/bin/bash
set -e

SILENT="true"

test() {
  if [ -z "${LAMBDACD_GIT_TESTREPO_USERNAME}" ] || [ -z "${LAMBDACD_GIT_TESTREPO_PASSWORD}" ]; then
    echo "Needs LAMBDACD_GIT_TESTREPO_USERNAME AND LAMBDACD_GIT_TESTREPO_PASSWORD"
    # TODO: skip tests instead
    exit 1
  elif [ "${SILENT}" == "true" ]; then
    lein with-profile dev,silent test
  else
    lein test
  fi
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
