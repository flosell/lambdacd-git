(ns lambdacd-git.git
  (:require [clojure.java.io :as io]
            [lambdacd.steps.support :as support])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib Ref TextProgressMonitor AnyObjectId)
           (java.io PrintWriter)
           (org.eclipse.jgit.revwalk RevCommit RevWalk)
           (java.util Date)))

(defn- ref->hash [^Ref ref]
  (-> ref
      (.getObjectId)
      (.name)))

(defn match-ref [ref]
  (fn [other-ref]
    (= other-ref ref)))

(defn match-ref-by-regex [regex]
  (fn [other-branch]
    (re-matches regex other-branch)))

(defn- entry-to-ref-and-hash [entry]
  [(key entry) (ref->hash (val entry))])

(defn current-revisions [remote ref-filter-pred]
  (let [ref-map (-> (Git/lsRemoteRepository)
                    (.setHeads true)
                    (.setTags true)
                    (.setRemote remote)
                    (.callAsMap))]
    (->> ref-map
         (filter #(ref-filter-pred (key %)))
         (map entry-to-ref-and-hash)
         (into {}))))

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

(defn clone-repo [repo cwd & {:keys [timeout]
                              :or   {timeout 20}}]
  (println "Cloning" repo "...")
  (-> (Git/cloneRepository)
      (.setURI repo)
      (.setDirectory (io/file cwd))
      (.setProgressMonitor (TextProgressMonitor. *out*))
      (.setTimeout timeout)
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
                (.getShortMessage))
        name (-> ref
                 (.getAuthorIdent)
                 (.getName))
        email (-> ref
                  (.getAuthorIdent)
                  (.getEmailAddress))
        time (-> ref
                 (.getCommitTime)
                 (* 1000)
                 (Date.))
        author (format "%s <%s>" name email)]
    {:hash      hash
     :msg       msg
     :author    author
     :timestamp time}))

(defn- ^Git git-open [workspace]
  (Git/open (io/file workspace)))

(defn commits-between [workspace from-hash to-hash]
  (let [git (git-open workspace)
        refs (-> git
                 (.log)
                 (.addRange (resolve-object git from-hash) (resolve-object git to-hash))
                 (.call))]
    (map process-commit (reverse refs))))

(defn- get-commit-reference [^Git git hash]
  (-> git
      (.getRepository)
      (RevWalk.)
      (.parseCommit (resolve-object git hash))))

(defn get-single-commit [workspace hash]
  (let [git (git-open workspace)]
    (-> git
        (get-commit-reference hash)
        (process-commit))))

(defn tag-revision [workspace hash tag]
  (let [git (git-open workspace)
        commit (get-commit-reference git hash)]
    (-> git
        (.tag)
        (.setObjectId commit)
        (.setName tag)
        (.call))))