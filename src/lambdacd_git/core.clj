(ns lambdacd-git.core
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [clojure.tools.logging :as log]
            [lambdacd.steps.support :as support]
            [lambdacd.presentation.pipeline-state :as pipeline-state]
            [lambdacd-git.git :as git]))

(defn report-git-exception [branch repo-uri e]
  (log/warn e (str "could not get current revision for branch " branch " on " repo-uri))
  (println "could not get current revision for branch" branch "on" repo-uri ":" (.getMessage e)))

(defn- revision-changed-from [last-seen-revision repo-uri branch]
  (try
    (let [new-revision (git/current-revision repo-uri branch)]
      (log/debug "waiting for new revision. current revision" new-revision "last seen" last-seen-revision)
      (when (not= last-seen-revision new-revision)
        {:status :success :revision new-revision :old-revision last-seen-revision}))
    (catch Exception e
      (report-git-exception branch repo-uri e)
      {:status :failure})))

(defn- wait-for-revision-changed-from [last-seen-revision repo-uri branch ctx ms-between-polls]
    (async/>!! (:result-channel ctx) [:status :waiting])
    (println "Last seen revision:" (or last-seen-revision "None") ". Waiting for new commit...")
    (loop [result (revision-changed-from last-seen-revision repo-uri branch)]
      (support/if-not-killed ctx
                             (if (= :success (:status result))
                               (do
                                 (println "Found new commit: " (:revision result) ".")
                                 result)
                               (do (Thread/sleep ms-between-polls)
                                   (recur (revision-changed-from last-seen-revision repo-uri branch)))))))

(defn- last-seen-revision-for-this-step [ctx repo-uri branch]
  (let [last-step-result (pipeline-state/most-recent-step-result-with :_git-last-seen-revision ctx)
        last-seen-revision-in-history (:_git-last-seen-revision last-step-result)]
    (if-not (nil? last-seen-revision-in-history)
      last-seen-revision-in-history
      (try
        (git/current-revision repo-uri branch)
        (catch Exception e
          (report-git-exception branch repo-uri e)
          nil)))))

(defn- persist-last-seen-revision [wait-for-result last-seen-revision ctx]
  (let [current-revision (:revision wait-for-result)
        revision-to-persist (or current-revision last-seen-revision)]
    (async/>!! (:result-channel ctx) [:_git-last-seen-revision revision-to-persist]) ; by sending it through the result-channel, we can be pretty sure users don't overwrite it
    (assoc wait-for-result :_git-last-seen-revision revision-to-persist)))

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx repo-uri branch & {:keys [ms-between-polls]
                          :or   {ms-between-polls (* 10 1000)}}]
  (support/capture-output ctx
    (let [last-seen-revision (last-seen-revision-for-this-step ctx repo-uri branch)
          wait-for-result (wait-for-revision-changed-from last-seen-revision repo-uri branch ctx ms-between-polls)]
      (persist-last-seen-revision wait-for-result last-seen-revision ctx))))

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