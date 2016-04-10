# Changelog

## 0.1.2

* Improvements
  * Supporting known hosts files other than `~/.ssh/known_hosts` (e.g. `/etc/ssh/ssh_known_hosts`). (#9)
    Call `lambdacd-git.core/init-ssh!` instead of `lambdacd-git.ssh-agent-support/initialize-ssh-agent-support!` (which is now deprecated)
  * `lambdcd-git/ssh-agent-support/initialize-ssh-agent-support!` is deprecated and will be removed in subsequent releases.
    It is being replaced with `lambdacd-git.core/init-ssh!`

## 0.1.1

* Improvements
  * Added a way to notify `wait-for-git` through HTTP POST requests (#6)

## 0.1.0

* Initial Release 