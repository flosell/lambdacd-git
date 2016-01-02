(ns lambdacd-git.example.multi-repo-pipeline
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
            [lambdacd-git.ssh-agent-support :as ssh-agent-support]
            [lambdacd.steps.support :as support]
            [lambdacd-git.git :as git]))

(def testrepo-remote "git@github.com:flosell/testrepo")
(def git-remote "git@github.com:flosell/lambdacd-git")

(defn wait-for [remote]
  (fn [args ctx]
    (core/wait-for-git ctx remote
                       :ref (git/match-branch "master")
                       :ms-between-polls 1000)))

(defn- revision-or-master [args remote]
  ; if a commit on this remote triggered the build, use the revision that triggered it,
  ; otherwise, use the head of master. wait-for-git supplies the changed remote
  (if (= (:changed-remote args) remote)
    (:revision args)
    "master"))

(defn clone [subdir ^:hide remote]
  (fn [args ctx]
    (let [revision (revision-or-master args remote)]
      (core/clone ctx remote revision (str (:cwd args) "/" subdir)))))

(defn list-files [args ctx]
  (shell/bash ctx (:cwd args)
              "tree -L 3"))

(def pipeline-structure
  `((either
      wait-for-manual-trigger
      (wait-for ~git-remote)
      (wait-for ~testrepo-remote))
     (with-workspace
       (in-parallel
         (clone "git" ~git-remote)
         (clone "testrepo" ~testrepo-remote))
       list-files)))

(defn -main [& args]
  (let [home-dir (util/create-temp-dir)
        config {:home-dir home-dir}
        pipeline (lambdacd/assemble-pipeline pipeline-structure config)]
    (ssh-agent-support/initialize-ssh-agent-support!)
    (runners/start-one-run-after-another pipeline)
    (ring-server/serve (ui/ui-for pipeline)
                                  {:open-browser? false
                                   :port 8082})))