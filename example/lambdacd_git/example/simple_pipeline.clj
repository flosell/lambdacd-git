(ns lambdacd-git.example.simple-pipeline
  (:use [compojure.core])
  (:require [lambdacd.steps.shell :as shell]
            [lambdacd.steps.manualtrigger :refer [wait-for-manual-trigger]]
            [lambdacd.steps.control-flow :refer [either with-workspace in-parallel run]]
            [lambdacd.core :as lambdacd]
            [ring.server.standalone :as ring-server]
            [lambdacd.util :as util]
            [lambdacd.ui.ui-server :as ui]
            [lambdacd-git.core :as core]
            [lambdacd.runners :as runners]
            [lambdacd-git.ssh-agent-support :as ssh-agent-support]))

(def repo "git@github.com:flosell/testrepo")

(defn wait-for-git [args ctx]
  (core/wait-for-git ctx repo
                     :ref "refs/heads/master"
                     :ms-between-polls (* 60 1000)))

(defn clone [args ctx]
  (core/clone ctx repo (:revision args) (:cwd args)))

(defn ls [args ctx]
  (shell/bash ctx (:cwd args) "ls"))

(def pipeline-structure
  `((either
      wait-for-manual-trigger
      wait-for-git)
     (with-workspace
       clone
       core/list-changes

       ls)))

(defn all-routes [pipeline]
  (routes
    (POST "/notify-git" request (core/notify-git-handler (:context pipeline) request))
    (context "" [] (ui/ui-for pipeline))))

(defn -main [& args]
  (let [home-dir (util/create-temp-dir)
        config {:home-dir home-dir}
        pipeline (lambdacd/assemble-pipeline pipeline-structure config)]
    (ssh-agent-support/initialize-ssh-agent-support!)
    (runners/start-one-run-after-another pipeline)
    (ring-server/serve (all-routes pipeline)
                                  {:open-browser? false
                                   :port 8082})))