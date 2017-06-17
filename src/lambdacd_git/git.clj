(ns lambdacd-git.git
  (:require [clojure.java.io :as io])
  (:import (org.eclipse.jgit.api Git TransportCommand)
           (org.eclipse.jgit.lib Ref TextProgressMonitor)
           (org.eclipse.jgit.revwalk RevCommit RevWalk)
           (java.util Date)
           (org.eclipse.jgit.transport CredentialsProvider)))

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

(defn- set-transport-opts [^TransportCommand transport-command {:keys [timeout ^CredentialsProvider credentials-provider]
                                                                :or   {timeout              20
                                                                       credentials-provider (CredentialsProvider/getDefault)}}]
  (-> transport-command
      (.setTimeout timeout)
      (.setCredentialsProvider credentials-provider)))

(defn current-revisions [remote ref-filter-pred git-config]
  (let [ref-map (-> (Git/lsRemoteRepository)
                    (set-transport-opts git-config)
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
    (ref-or-nil git ref)))

(defn clone-repo [repo cwd git-config]
  (println "Cloning" repo "...")
  (-> (Git/cloneRepository)
      (set-transport-opts git-config)
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
  (println "Tagging " hash " with " tag "...")
  (let [git (git-open workspace)
        commit (get-commit-reference git hash)]
    (-> git
        (.tag)
        (.setObjectId commit)
        (.setName tag)
        (.call))))

(defn push [workspace remote git-config]
  (println "Pushing changes...")
  (let [git (git-open workspace)]
    (-> git
        (.push)
        (set-transport-opts git-config)
        (.setPushAll)
        (.setPushTags)
        (.setRemote remote)
        (.setProgressMonitor (TextProgressMonitor. *out*))
        (.call))))
