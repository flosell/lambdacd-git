# Git support for LambdaCD

Provides Git support for [LambdaCD](https://github.com/flosell/lambdacd).
Will replace the `lambdacd.steps.git` namespace in the the lambdacd-git library.

## Status

[![Build Status](https://travis-ci.org/flosell/lambdacd-git.svg)](https://travis-ci.org/flosell/lambdacd-git)

[![Clojars Project](http://clojars.org/lambdacd-git/latest-version.svg)](http://clojars.org/lambdacd-git)

## Usage

```clojure
;; project.clj
:dependencies [[lambdacd-git "<most recent version>"]]
;; import:
(:require [lambdacd-git.core :as lambdacd-git])
```

### Complete Example

You'll find a complete example here: [example/simple_pipeline.clj](https://github.com/flosell/lambdacd-git/blob/master/example/lambdacd_git/example/simple_pipeline.clj)

### Waiting for a commit

```clojure
(defn wait-for-commit-on-master [args ctx]
  (lambdacd-git/wait-for-git ctx "git@github.com:flosell/testrepo"
                             ; how long to wait when polling. optional, defaults to 10000
                             :ms-between-polls 1000
                             ; which refs to react to. optional, defaults to refs/heads/master
                             :ref "refs/heads/master"))

; you can also pass in a regex:
(defn wait-for-commit-on-feature-branch [args ctx]
  (lambdacd-git/wait-for-git ctx "git@github.com:flosell/testrepo"
                             :ref #"refs/heads/feature-.*"))

; you can also pass in a function
(defn wait-for-commit-on-any-tag [args ctx]
  (lambdacd-git/wait-for-git ctx "git@github.com:flosell/testrepo"
                             :ref (fn [ref] (.startsWith ref "refs/tags/"))))
```

### Using Web- or Post-Commit Hooks instead of Polling

`wait-for-git` can be notified about changes in a repository through an HTTP endpoint. To use this, you need to add it
to your existing ring-handlers:

```clojure
(ring-server/serve (routes
                         (ui/ui-for pipeline)
                         (core/notifications-for pipeline)) ; <-- THIS
                       {:open-browser? false
                        :port          8082})
```

This adds an HTTP endpoint that can receive POST requests on `<host>/notify-git?remote=<remote>`,
e.g. `http://localhost:8082/notify-git?remote=git@github.com:flosell/testrepo`

### Cloning a Repository

```clojure
(defn clone [args ctx]
  (lambdacd-git/clone ctx repo branch-or-tag-or-commit-hash (:cwd args)))

(def pipeline-structure
  `(; ...
    (with-workspace
      clone
      do-something)))

; Works well with wait-for-git: 
; If no revision is given (e.g. because of manual trigger), clone falls back to the head of the master branch

(defn clone [args ctx]
  (lambdacd-git/clone ctx repo (:revision args) (:cwd args)))

(def pipeline-structure
  `((either
      wait-for-manual-trigger
      wait-for-git)
     (with-workspace
       clone

       do-something)))
```

### Get details on commits since last build

```clojure
(defn clone [args ctx]
  (lambdacd-git/clone ctx repo branch-or-tag-or-commit-hash (:cwd args)))

(def pipeline-structure
  `(wait-for-git
    (with-workspace
      clone
      lambdacd-git/list-changes
      do-something)))
```

### Working with more than one repository

You can have clone steps that clone into different subdirectories: 

```clojure
(defn clone-foo [args ctx]
  (lambdacd-git/clone ctx repo branch-or-tag-or-commit-hash (str (:cwd args) "/" "foo")))
(defn clone-bar [args ctx]
  (lambdacd-git/clone ctx repo branch-or-tag-or-commit-hash (str (:cwd args) "/" "bar")))

(def pipeline-structure
  `(; ... 
    (with-workspace
      clone-foo
      clone-bar
      do-something)))
```

If you want to use this in combination with `wait-for-git`, you need to detect which commit to use. For details, see
[example/multi_repo_pipeline.clj](https://github.com/flosell/lambdacd-git/blob/master/example/lambdacd_git/example/multi_repo_pipeline.clj)

### Tagging versions

You can tag any revision:

```clojure
(defn deploy-to-live [args ctx]
  ;do deploy
  (let [cwd (:cwd args)]
    (lambdacd-git/tag-version ctx cwd repo "HEAD" (str "live-" version))))

(def pipeline-structure
  `(; ...
    (with-workspace
      ;...
      deploy-to-live
      do-something)))
```

### SSH Configuration

LambdaCD-Git honors the default [SSH Config files](https://linux.die.net/man/5/ssh_config) from `~/.ssh/config`(`/etc/ssh/ssh_config` currently not supported (see [#23](https://github.com/flosell/lambdacd-git/issues/23))). Use this to configure things like `StrictHostKeyChecking` or the `IdentityFile`. Alternatively, some options can be configured using `ssh-init`.

### Authentication

#### Git over SSH

LambdaCD Git automatically picks up SSH-Keys in the default locations, e.g. `~/.ssh/id_rsa`. SSH Agents are also supported. 
 
#### Git over HTTPS

Authentication for HTTPS is supported using an instance of the JGit [`CredentialsProvider`](http://download.eclipse.org/jgit/site/4.1.1.201511131810-r/apidocs/org/eclipse/jgit/transport/CredentialsProvider.html). 


Import e.g. [`UsernamePasswordCredentialsProvider`](http://download.eclipse.org/jgit/site/4.1.1.201511131810-r/apidocs/org/eclipse/jgit/transport/UsernamePasswordCredentialsProvider.html) into your namespace:
```clojure
(ns <your-pipeline-name>.core
  ;...
  (:import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider)
  ;...
  )
```

Add an instance to the LambdaCD config: 
 
```clojure
(let [config {:home-dir "/some/path"
              :git {:credentials-provider (UsernamePasswordCredentialsProvider. "some-username" "some-password")}}]
              ; ... 
              )
```

### Customize SSH Client

Some features of lambdacd-git (ssh-agent support, extended known_hosts support) require customizations to JGits singleton
SSH session factory. Call `init-ssh!` once, e.g. in your `-main` function:

```clojure
(defn -main [& args]
  ; ...
  (lambdacd-git/init-ssh!)
  ; ...
  )
```


## Development

Call `./go`

## License

Copyright © 2015 Florian Sellmayr

Distributed under the Apache License 2.0
