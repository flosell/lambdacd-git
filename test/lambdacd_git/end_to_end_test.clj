(ns lambdacd-git.end-to-end-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.core :as core]
            [lambdacd.core :as lambdacd]
            [lambdacd.util :as lambdacd-util]
            [lambdacd.state.core :as lambdacd-state]
            [lambdacd.execution :as lambdacd-execution]
            [lambdacd-git.example.simple-pipeline :as simple-pipeline]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [lambdacd.steps.shell :as shell]
            [lambdacd-git.test-utils :as test-utils]
            [lambdacd.steps.control-flow :refer [either with-workspace]]
            [lambdacd.steps.manualtrigger :refer [wait-for-manual-trigger]])
  (:import (org.eclipse.jgit.transport CredentialsProvider UsernamePasswordCredentialsProvider)))

(defn match-all-refs [_]
  true)

(defn wait-for-git [args ctx]
  (core/wait-for-git ctx (:repo-uri (:config ctx))
                     :ref match-all-refs
                     :ms-between-polls (* 1 1000)))

(defn clone [args ctx]
  (core/clone ctx (:repo-uri (:config ctx)) (:revision args) (:cwd args)))

(defn ls [args ctx]
  (shell/bash ctx (:cwd args) "ls"))

(defn create-new-tag [args ctx]
  (core/tag-version ctx (:cwd args) (:repo-uri (:config ctx)) "HEAD" (str (System/currentTimeMillis))))

(def pipeline-structure
  `((either
      wait-for-manual-trigger
      wait-for-git)
     (with-workspace
       clone
       core/list-changes
       ls
       create-new-tag)))

; ======================================================================================================================



(defn trigger-id [ctx build-number step-id]
  (let [step-result (lambdacd-state/get-step-result ctx build-number step-id)]
    (:trigger-id step-result)))

(defn- trigger-manually [pipeline]
  (let [ctx (:context pipeline)]
    (test-utils/while-with-timeout 10000 (nil? (trigger-id ctx 1 [1 1]))
                        (Thread/sleep 1000))
    (manualtrigger/post-id ctx (trigger-id ctx 1 [1 1]) {})))

(deftest example-pipeline-test
  (testing "the example-pipeline"
    (let [config                 {:home-dir (lambdacd-util/create-temp-dir)}
          pipeline               (lambdacd/assemble-pipeline simple-pipeline/pipeline-structure config)
          future-pipeline-result (future
                                   (lambdacd-execution/run (:pipeline-def pipeline) (:context pipeline)))]
      (trigger-manually pipeline)
      (let [pipeline-result (deref future-pipeline-result 60000 :timeout)]
        (is (= :success (:status pipeline-result)) (str "No success in " pipeline-result))))))

(deftest ^:e2e-with-auth end-to-end-with-auth-test
  (testing "a complete pipeline with all features against private repositories using https and ssh"
    (doseq [repo-config [{:repo-uri (or (System/getenv "LAMBDACD_GIT_TESTREPO_SSH") "git@gitlab.com:flosell-test/testrepo.git")}
                         {:repo-uri (or (System/getenv "LAMBDACD_GIT_TESTREPO_HTTPS") "https://gitlab.com/flosell-test/testrepo.git")
                          :git      {:credentials-provider (UsernamePasswordCredentialsProvider. (System/getenv "LAMBDACD_GIT_TESTREPO_USERNAME")
                                                                                                 (System/getenv "LAMBDACD_GIT_TESTREPO_PASSWORD"))}}]]
      (testing (:repo-uri repo-config)
        (let [config                 (assoc repo-config :home-dir (lambdacd-util/create-temp-dir))
              pipeline               (lambdacd/assemble-pipeline pipeline-structure config)
              future-pipeline-result (future
                                       (lambdacd-execution/run (:pipeline-def pipeline) (:context pipeline)))]
          (trigger-manually pipeline)
          (let [pipeline-result (deref future-pipeline-result 60000 :timeout)]
            (is (= :success (:status pipeline-result)) (str "No success in " pipeline-result)))
          ; Run pipeline again, this time it should be triggered by wait-for git that picks up the tag created in the previous run
          (let [future-second-pipeline-result (future
                                                (lambdacd-execution/run (:pipeline-def pipeline) (:context pipeline)))
                pipeline-result               (deref future-second-pipeline-result 60000 :timeout)]
            (is (= :success (:status pipeline-result)) (str "No success in " pipeline-result))))))))

