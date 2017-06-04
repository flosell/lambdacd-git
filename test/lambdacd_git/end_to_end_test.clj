(ns lambdacd-git.end-to-end-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.core :as core]
            [lambdacd.core :as lambdacd]
            [lambdacd.util :as lambdacd-util]
            [lambdacd.state.core :as lambdacd-state]
            [lambdacd.execution :as lambdacd-execution]
            [lambdacd-git.example.simple-pipeline :as simple-pipeline]
            [lambdacd-git.git-utils :as git-utils]
            [lambdacd.steps.manualtrigger :as manualtrigger]))

(defmacro while-with-timeout [timeout-ms test & body]
  `(let [start-timestamp# (System/currentTimeMillis)]
     (while (and
              ~test
              (< (System/currentTimeMillis) (+ start-timestamp# ~timeout-ms)))
       ~@body)))

(defn trigger-id [ctx build-number step-id]
  (let [step-result (lambdacd-state/get-step-result ctx build-number step-id)]
    (:trigger-id step-result)))

(defn- trigger-manually [pipeline]
  (let [ctx (:context pipeline)]
    (while-with-timeout 10000 (nil? (trigger-id ctx 1 [1 1]))
                        (Thread/sleep 1000))
    (manualtrigger/post-id ctx (trigger-id ctx 1 [1 1]) {})))

(deftest end-to-end-test
  (testing "a complete pipeline"
    (core/init-ssh!)
    (doseq [repo-uri ["https://github.com/flosell/testrepo.git"]]
      (testing (str "repo-uri" repo-uri)
        (let [config                 {:home-dir (lambdacd-util/create-temp-dir)
                                      :repo-uri repo-uri}
              pipeline               (lambdacd/assemble-pipeline simple-pipeline/pipeline-structure config)
              future-pipeline-result (future
                                       (lambdacd-execution/run (:pipeline-def pipeline) (:context pipeline)))]
          (trigger-manually pipeline)
          (let [pipeline-result (deref future-pipeline-result 60000 :timeout)]
            (is (= :success (:status pipeline-result)) (str "No success in " pipeline-result))))))))

