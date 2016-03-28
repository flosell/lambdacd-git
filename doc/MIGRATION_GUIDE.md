# Background

`lambdacd-git` is a re-implementation of LambdaCDs git-support that aims to make Git-support more robust, friendlier to
contributions and more flexible in usage.

This required extracting git-support into its own library and changing some of the interfaces you got used to.
The following guide is intended to make this migration as painless as possible, not necessarily to give you all the power
the new library provides. So after you are done migrating, consider spending a bit more time to refactor your codebase
to take advantage of the new interfaces and features.

# Migrating from the built-in `lambdacd.git` implementation

## `wait-for-git`

The interface for this function is almost the same as before. Only the `branch` argument was removed in favor of a more
general ref-filter:

```clojure
; old
(lambdacd.steps.git/wait-for-git ctx "git@github.com:flosell/testrepo.git" "some-branch" :ms-between-polls 100)
(lambdacd.steps.git/wait-for-git ctx "git@github.com:flosell/testrepo.git" "master" :ms-between-polls 100)

; new
(lambdacd-git.core/wait-for-git ctx "git@github.com:flosell/testrepo.git" :ms-between-polls 100 :ref "refs/heads/some-branch")
(lambdacd-git.core/wait-for-git ctx "git@github.com:flosell/testrepo.git" :ms-between-polls 100) ; ref defaults to "refs/heads/master"
```

## `wait-with-details`

This function has been removed as mixes too many concerns with each other. Those concerns are now separated:

* waiting for a commit ([`wait-for-git`](https://github.com/flosell/lambdacd-git#waiting-for-a-commit))
* cloning a repo ([`clone`](https://github.com/flosell/lambdacd-git#cloning-a-repository))
* generating a changelog ([`list-changes`](https://github.com/flosell/lambdacd-git#get-details-on-commits-since-last-build))

TODO: add convenience example on how to get exactly the same behavior?

## `with-git`/`with-git-branch`/`checkout-and-execute`

These functions have been removed as they are either redundant or mix too many concerns.
Use LambdaCDs `with-workspace` together with [`clone`](https://github.com/flosell/lambdacd-git#cloning-a-repository) instead.

TODO: add convenience example on how to get exactly the same behavior?