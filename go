#!/bin/bash
set -e

SILENT="true"

create-testrepo() {
  curl -sSf -H 'Private-Token: 9PLYdwG7ZXhz2zzUKoCe' --output /dev/null -XPOST "https://gitlab.com/api/v4/projects?name=${TESTREPO_NAME}"
  tmpdir=$(mktemp -d)
  git clone "${LAMBDACD_GIT_TESTREPO_SSH}"
  cd "${TESTREPO_NAME}"
  touch hello
  git add hello
  git commit -m "initializing"
  git push origin master
  cd -
}

delete-testrepo() {
  rm -rf "${TESTREPO_NAME}"
  curl -sSf -H "Private-Token: ${LAMBDACD_GIT_TESTREPO_PASSWORD}" --output /dev/null -XDELETE "https://gitlab.com/api/v4/projects/${TESTREPO_PATH}"
}

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

  if [ -n "${TRAVIS_JOB_NUMBER}" ]; then
    TESTREPO_NAME="testrepo-$(echo "${TRAVIS_JOB_NUMBER}" | sed -e 's/\./-/g')"
    TESTREPO_PATH="flosell-test%2F${TESTREPO_NAME}"

    export LAMBDACD_GIT_TESTREPO_SSH="git@gitlab.com:flosell-test/${TESTREPO_NAME}.git"
    export LAMBDACD_GIT_TESTREPO_HTTPS="https://gitlab.com/flosell-test/${TESTREPO_NAME}.git"

    trap delete-testrepo EXIT
    create-testrepo
  fi

  ${CMD}
}

push() {
  test && git push
}

release() {
  test && lein release && scripts/github-release.sh
}

clean-up-testrepo() {
  tmp_dir=$(mktemp -d)
  git clone git@gitlab.com:flosell-test/testrepo.git "${tmp_dir}"
  pushd "${tmp_dir}" > /dev/null
    git tag -l | xargs -n 50 git push --delete origin
  popd > /dev/null
  rm -rf "${tmp_dir}"
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
    test               -- run all tests
    clean-up-testrepo  -- clean up temporary data left behind in by end-to-end tests in test-repo
    push               -- run all tests and push current state
    run                -- run the simple sample pipeline
    run multi-repo     -- run the multi-repo sample pipeline
    release            -- release current version"

    exit 1
fi
