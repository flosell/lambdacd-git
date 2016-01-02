(ns lambdacd-git.core
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [clojure.tools.logging :as log]
            [lambdacd.steps.support :as support]
            [lambdacd.presentation.pipeline-state :as pipeline-state]
            [lambdacd-git.git :as git]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io])
  (:import (java.util.regex Pattern)))

(defn report-git-exception [branch repo-uri e]
  (log/warn e (str "could not get current revision for branch " branch " on " repo-uri))
  (println "could not get current revision for branch" branch "on" repo-uri ":" (.getMessage e)))

(defn- find-changed-ref [old-revisions new-revisions]
  (let [[_ new-entries _]  (diff old-revisions new-revisions)
        changed-ref        (->> new-entries
                                (first)
                                (key))
        last-seen-revision (get old-revisions changed-ref)
        new-revision       (get new-revisions changed-ref)]
    {:status :success
     :revision new-revision
     :old-revision last-seen-revision
     :all-revisions new-revisions}))

(defn- revisions-changed-from [last-seen-revisions repo-uri branch]
  (try
    (let [new-revisions (git/current-revisions repo-uri branch)]
      (log/debug "waiting for new revision. current revisions:" new-revisions "last seen:" last-seen-revisions)
      (when (not= last-seen-revisions new-revisions)
        (find-changed-ref last-seen-revisions new-revisions)))
    (catch Exception e
      (report-git-exception branch repo-uri e)
      {:status :failure})))

(defn- wait-for-revision-changed-from [last-seen-revisions repo-uri branch ctx ms-between-polls]
    (async/>!! (:result-channel ctx) [:status :waiting])
    (println "Last seen revisions:" (or last-seen-revisions "None") ". Waiting for new commit...")
    (loop [result (revisions-changed-from last-seen-revisions repo-uri branch)]
      (support/if-not-killed ctx
                             (if (= :success (:status result))
                               (do
                                 (println "Found new commit: " (:revision result) ".")
                                 result)
                               (do (Thread/sleep ms-between-polls)
                                   (recur (revisions-changed-from last-seen-revisions repo-uri branch)))))))

(defn- last-seen-revisions-for-this-step [ctx repo-uri branch]
  (let [last-step-result (pipeline-state/most-recent-step-result-with :_git-last-seen-revisions ctx)
        last-seen-revisions-in-history (:_git-last-seen-revisions last-step-result)]
    (if-not (nil? last-seen-revisions-in-history)
      last-seen-revisions-in-history
      (try
        (git/current-revisions repo-uri branch)
        (catch Exception e
          (report-git-exception branch repo-uri e)
          nil)))))

(defn- persist-last-seen-revisions [wait-for-result last-seen-revisions ctx]
  (let [current-revisions (:all-revisions wait-for-result)
        revisions-to-persist (or current-revisions last-seen-revisions)]
    (async/>!! (:result-channel ctx) [:_git-last-seen-revisions revisions-to-persist]) ; by sending it through the result-channel, we can be pretty sure users don't overwrite it
    (assoc wait-for-result :_git-last-seen-revisions revisions-to-persist)))

(defn- regex? [x]
  (instance? Pattern x))

(defn- to-branch-pred [branch-spec]
  (cond
    (string? branch-spec) (git/match-branch branch-spec)
    (regex? branch-spec) (git/match-branch-by-regex branch-spec)
    :else branch-spec))

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx repo-uri & {:keys [branch ms-between-polls]
                   :or   {ms-between-polls (* 10 1000)
                          branch           nil}}]
  (support/capture-output ctx
    (if branch
      (let [last-seen-revisions (last-seen-revisions-for-this-step ctx repo-uri (to-branch-pred branch))
            wait-for-result (wait-for-revision-changed-from last-seen-revisions repo-uri (to-branch-pred branch) ctx ms-between-polls)]
        (persist-last-seen-revisions wait-for-result last-seen-revisions ctx))
      (do
        (println "Waiting for a commit without specifying a branch is unsupported at this time")
        {:status :failure}))))

(defn clone [ctx repo ref cwd]
  (support/capture-output ctx
                          (let [git (git/clone-repo repo cwd)
                                existing-ref (git/find-ref git ref)]
                            (if existing-ref
                              (do
                                (git/checkout-ref git existing-ref)
                                {:status :success})
                              (do
                                (println "Failure: Could not find ref" ref)
                                {:status :failure})))))

(defn- print-commit [commit]
  (println (:hash commit) "|" (:msg commit)))

(defn- output-commits [commits]
  (doall
    (map print-commit commits))
  {:status :success
   :commits commits})

(defn- failure [msg]
  {:status :failure :out msg})

(defn no-git-repo? [cwd]
  (not (.exists (io/file cwd ".git"))))

(defn list-changes [args ctx]
  (support/capture-output ctx
    (let [old-revision (:old-revision args)
          new-revision (:revision args)
          cwd (:cwd args)]
      (cond
        (nil? cwd) (failure "No working directory (:cwd) defined. Did you clone the repository?")
        (no-git-repo? cwd) (failure "No .git directory found in working directory. Did you clone the repository?")
        (or (nil? old-revision) (nil? new-revision)) (do
                                                       (println "No old or current revision found.")
                                                       (println "Current HEAD:")
                                                       (output-commits [(git/get-single-commit cwd "HEAD")]))
        :else (output-commits (git/commits-between cwd old-revision new-revision))))))