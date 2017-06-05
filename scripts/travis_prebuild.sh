#!/bin/bash

SCRIPT_DIR=$(dirname "$0")

set -e

# We have tests that deal with git. They need to have this set to pass
git config --global user.email "you@example.com"
git config --global user.name "Your Name"

# decrypt ssh key to access test-gitlab:
if [ -z "${encrypted_766accd2732a_key}" ] || [ -z "${encrypted_766accd2732a_iv}" ]; then
  echo "SKIPPING GITLAB SSH KEY DECRYPTION, no TravisCI encryption keys found."
  exit 0;
else
  mkdir -p ~/.ssh
  openssl aes-256-cbc -K "${encrypted_766accd2732a_key}" -iv "${encrypted_766accd2732a_iv}" -in "${SCRIPT_DIR}/id_rsa_gitlab-test.enc" -out ~/.ssh/id_rsa -d
  cat >> ~/.ssh/config <<EOF
Host gitlab.com
  StrictHostKeyChecking no
EOF
fi
