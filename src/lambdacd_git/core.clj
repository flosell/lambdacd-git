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

(defn- find-changed-revision [old-revisions new-revisions]
  (let [[_ new-entries _] (diff old-revisions new-revisions)
        changed-ref        (->> new-entries
                                (first)
                                (key))
        last-seen-revision (get old-revisions changed-ref)
        new-revision       (get new-revisions changed-ref)]
    {:changed-ref  changed-ref
     :revision     new-revision
     :old-revision last-seen-revision}))

(defn- report-git-exception [branch remote e]
  (log/warn e (str "could not get current revision for branch " branch " on " remote))
  (println "could not get current revision for branch" branch "on" remote ":" (.getMessage e)))

(defn- current-revision-or-nil [remote branch]
  (try
    (git/current-revisions remote branch)
    (catch Exception e
      (report-git-exception branch remote e)
      nil)))

(defn- found-new-commit [last-seen-revisions current-revisions]
  (let [changes (find-changed-revision last-seen-revisions current-revisions)]
    (println "Found new commit: " (:revision changes) "on" (:changed-ref changes))
    {:status        :success
     :changed-ref   (:changed-ref changes)
     :revision      (:revision changes)
     :old-revision  (:old-revision changes)
     :all-revisions current-revisions}))

(defn- wait-for-revision-changed [last-seen-revisions remote branch ctx ms-between-polls]
  (println "Last seen revisions:" (or last-seen-revisions "None") ". Waiting for new commit...")
  (loop [last-seen-revisions last-seen-revisions]
    (support/if-not-killed ctx
      (let [current-revisions (current-revision-or-nil remote branch)]
        (if (and
              (not (nil? current-revisions))
              (not= current-revisions last-seen-revisions))
          (found-new-commit last-seen-revisions current-revisions)
          (do
            (Thread/sleep ms-between-polls)
            (recur current-revisions)))))))

(defn- last-seen-revisions-from-history [ctx]
  (let [last-step-result (pipeline-state/most-recent-step-result-with :_git-last-seen-revisions ctx)]
    (:_git-last-seen-revisions last-step-result)))

(defn- initial-revisions [ctx remote branch]
  (or
    (last-seen-revisions-from-history ctx)
    (current-revision-or-nil remote branch)))

(defn- persist-last-seen-revisions [wait-for-result last-seen-revisions ctx]
  (let [current-revisions    (:all-revisions wait-for-result)
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

(defn- report-waiting-status [ctx]
  (async/>!! (:result-channel ctx) [:status :waiting]))

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx remote & {:keys [branch ms-between-polls]
                   :or   {ms-between-polls (* 10 1000)
                          branch           "master"}}]
  (support/capture-output ctx
    (report-waiting-status ctx)
    (let [branch-pred       (to-branch-pred branch)
          initial-revisions (initial-revisions ctx remote branch-pred)
          wait-for-result   (wait-for-revision-changed initial-revisions remote branch-pred ctx ms-between-polls)]
      (persist-last-seen-revisions wait-for-result initial-revisions ctx))))

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