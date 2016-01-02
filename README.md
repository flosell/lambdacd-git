# Git support for LambdaCD

Provides Git support for [LambdaCD](https://github.com/flosell/lambdacd).
Will replace the `lambdacd.steps.git` namespace in the the lambdacd-git library.

## Status

[![Build Status](https://travis-ci.org/flosell/lambdacd-git.svg)](https://travis-ci.org/flosell/lambdacd-git)

[![Clojars Project](http://clojars.org/lambdacd-git/latest-version.svg)](http://clojars.org/lambdacd-git)

This library is under development and no stable version has been released yet.

## Usage

```clojure
;; project.clj
:dependencies [[lambdacd-git "<most recent version>"]]
;; import:
(:require [lambdacd-git.lambdacd-git :as lambdacd-git])
```

### Example Pipeline

You'll find a full example here: https://github.com/flosell/lambdacd-git/blob/master/example/lambdacd_git/example/pipeline.clj

### Waiting for a commit

```clojure
(defn wait-for-git [args ctx]
      (lambdacd-git/wait-for-git ctx "git@github.com:flosell/testrepo"
                     :branch "master"
                     :ms-between-polls 1000))
```

### Cloning a Repository

```clojure
(defn clone [args ctx]
  (lambdacd-git/clone ctx repo branch (:cwd args)))

(def pipeline-structure
  `(; ...
    (with-workspace
      clone
      do-something)))
```

## Get details on commits since last build

```clojure
(defn clone [args ctx]
  (lambdacd-git/clone ctx repo branch (:cwd args)))

(def pipeline-structure
  `(wait-for-git
    (with-workspace
      clone
      lambdacd-git/list-changes
      do-something)))
```

## Development

Call `./go`

## License

Copyright © 2015 Florian Sellmayr

Distributed under the Apache License 2.0
