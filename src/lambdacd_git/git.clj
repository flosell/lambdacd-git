(ns lambdacd-git.git
  (:require [clojure.java.io :as io]
            [lambdacd.steps.support :as support])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib Ref TextProgressMonitor AnyObjectId)
           (java.io PrintWriter)
           (org.eclipse.jgit.revwalk RevCommit RevWalk)))

(defn- ref->hash [^Ref ref]
  (-> ref
      (.getObjectId)
      (.name)))

(defn current-revision [remote branch]
  (-> (Git/lsRemoteRepository)
      (.setHeads true)
      (.setRemote remote)
      (.callAsMap)
      (get (str "refs/heads/" branch))
      (ref->hash)))

(defn resolve-object [git s]
  (-> git
      (.getRepository)
      (.resolve s)))

(defn- ref-exists? [git ref]
  (-> git
      (resolve-object ref)
      (nil?)
      (not)))

(defn- ref-or-nil [git ref]
  (if (ref-exists? git ref)
    ref
    nil))

(defn find-ref [git ref]
  (or
    (ref-or-nil git (str "origin/" ref))
    (ref-or-nil git ref))
  )

(defn clone-repo [repo cwd]
  (println "Cloning" repo "...")
  (-> (Git/cloneRepository)
      (.setURI repo)
      (.setDirectory (io/file cwd))
      (.setProgressMonitor (TextProgressMonitor. *out*))
      (.call)))

(defn checkout-ref [^Git git ref]
  (println "Checking out" ref "...")
  (-> git
      (.checkout)
      (.setName ref)
      (.call)))

(defn- process-commit [^RevCommit ref]
  (let [hash (-> ref
                 (.getId)
                 (.name))
        msg (-> ref
                (.getShortMessage))]
    {:hash hash
     :msg  msg}))

(defn- ^Git git-open [workspace]
  (Git/open (io/file workspace)))

(defn commits-between [workspace from-hash to-hash]
  (let [git (git-open workspace)
        refs (-> git
                 (.log)
                 (.addRange (resolve-object git from-hash) (resolve-object git to-hash))
                 (.call))]
    (map process-commit (reverse refs))))

(defn get-single-commit [workspace hash]
  (let [git (git-open workspace)]
    (-> git
        (.getRepository)
        (RevWalk.)
        (.parseCommit (resolve-object git hash))
        (process-commit))))