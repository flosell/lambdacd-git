(ns lambdacd-git.example.pipeline
  (:use [compojure.core]
        [lambdacd.steps.control-flow])
  (:require [lambdacd.steps.shell :as shell]
            [lambdacd.steps.manualtrigger :refer [wait-for-manual-trigger]]
            [lambdacd.core :as lambdacd]
            [ring.server.standalone :as ring-server]
            [lambdacd.util :as util]
            [lambdacd.ui.ui-server :as ui]
            [lambdacd-git.core :as core]
            [lambdacd.runners :as runners]
            [lambdacd-git.ssh-agent-support :as ssh-agent-support]))

; TODO: namespace inconsistency? with-workspace from core, clone from git

(def repo "git@github.com:flosell/lambdacd")
(def branch "master")

(defn wait-for-git [args ctx]
  (core/wait-for-git ctx repo branch :ms-between-polls 1000))

(defn clone [args ctx]
  (core/clone ctx repo branch (:cwd args)))

(defn ls [args ctx]
  (shell/bash ctx (:cwd args) "ls"))

(def pipeline-structure
  `((either
      wait-for-manual-trigger
      wait-for-git)
     (with-workspace
       clone
       ls)))

(defn -main [& args]
  (let [home-dir (util/create-temp-dir)
        config {:home-dir home-dir}
        pipeline (lambdacd/assemble-pipeline pipeline-structure config)]
    (ssh-agent-support/initialize-ssh-agent-support!)
    (runners/start-one-run-after-another pipeline)
    (ring-server/serve (ui/ui-for pipeline)
                                  {:open-browser? false
                                   :port 8082})))