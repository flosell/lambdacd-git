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
            [lambdacd.steps.manualtrigger :refer [wait-for-manual-trigger]]
            [lambdacd-git.ssh :as ssh])
  (:import (org.eclipse.jgit.transport CredentialsProvider UsernamePasswordCredentialsProvider SshSessionFactory)))

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

(defn- trigger-manually-internal [pipeline]
  (let [ctx (:context pipeline)]
    (test-utils/while-with-timeout 10000 (nil? (trigger-id ctx 1 [1 1]))
                                   (Thread/sleep 1000))
    (manualtrigger/post-id ctx (trigger-id ctx 1 [1 1]) {})))

(defn- init-state [& {:keys [config pipeline-structure]}]
  (atom {:config             config
         :pipeline           (lambdacd/assemble-pipeline pipeline-structure config)
         :pipeline-structure pipeline-structure}))

(defn- start-pipeline [state]
  (let [pipeline-structure (:pipeline-structure @state)
        ctx                (:context (:pipeline @state))]
    (swap! state #(assoc % :future-pipeline-result
                           (future
                             (lambdacd-execution/run pipeline-structure ctx)))))
  state)

(defn- trigger-manually [state]
  (trigger-manually-internal (:pipeline @state))
  state)

(defn pipeline-result [state]
  (deref (:future-pipeline-result @state) 60000 :timeout))

(defn wait-for-completion [state]
  (pipeline-result state)
  state)

(defmacro expect-status [state status]
  ; macro is inlined, therefore convinces cursive to mark the call location of the check as failed, not the implementation
  `(let [pipeline-result# (pipeline-result ~state)]
     (is (= ~status (:status pipeline-result#)) (str "No success in " pipeline-result#))
     ~state))

(defmacro expect-success [state]
  `(expect-status ~state :success))

(defmacro expect-failure [state]
  `(expect-status ~state :failure))

(defn- init-ssh [state k v]
  (let [existing-session-factory (SshSessionFactory/getInstance)]
    (swap! state #(assoc % :existing-session-factory existing-session-factory))
    (core/init-ssh! k v)
    state))

(defn- reset-init-ssh [state]
  (let [existing-session-factory (:existing-session-factory @state)]
    (SshSessionFactory/setInstance existing-session-factory)
    (swap! state #(dissoc % :existing-session-factory))
    (reset! ssh/init-ssh-called? false)
    state))

; ======================================================================================================================

(deftest example-pipeline-test
  (testing "the example-pipeline"
    (-> (init-state :config {:home-dir (lambdacd-util/create-temp-dir)}
                    :pipeline-structure simple-pipeline/pipeline-structure)
        (start-pipeline)
        (trigger-manually)
        (wait-for-completion)
        (expect-success))))

(deftest ^:e2e-with-auth end-to-end-with-auth-test
  (testing "a complete pipeline with all features against private repositories using https and ssh"
    (doseq [repo-config [{:repo-uri (or (System/getenv "LAMBDACD_GIT_TESTREPO_SSH") "git@gitlab.com:flosell-test/testrepo.git")
                          :git      {:ssh {:strict-host-key-checking "no"}}}
                         {:repo-uri (or (System/getenv "LAMBDACD_GIT_TESTREPO_HTTPS") "https://gitlab.com/flosell-test/testrepo.git")
                          :git      {:credentials-provider (UsernamePasswordCredentialsProvider. (System/getenv "LAMBDACD_GIT_TESTREPO_USERNAME")
                                                                                                 (System/getenv "LAMBDACD_GIT_TESTREPO_PASSWORD"))}}]]
      (testing (:repo-uri repo-config)
        (-> (init-state :config (assoc repo-config :home-dir (lambdacd-util/create-temp-dir))
                        :pipeline-structure pipeline-structure)
            (start-pipeline)
            (trigger-manually)
            (wait-for-completion)

            (expect-success)

            (start-pipeline)
            (wait-for-completion)

            (expect-success))))))

(deftest ^:e2e-with-auth backwards-compatibility-test
  (testing "that git operations on ssh fail if init-ssh was called and ssh config was also given"
    (-> (init-state :config {:repo-uri (or (System/getenv "LAMBDACD_GIT_TESTREPO_SSH") "git@gitlab.com:flosell-test/testrepo.git")
                             :git      {:ssh {:strict-host-key-checking "yes"}}
                             :home-dir (lambdacd-util/create-temp-dir)}
                    :pipeline-structure pipeline-structure)
        (init-ssh :strict-host-key-checking "no")
        (start-pipeline)
        (trigger-manually)
        (wait-for-completion)

        (expect-failure)

        (reset-init-ssh)))
  (testing "that git operations on ssh don't fail if no ssh config was given"
    (-> (init-state :config {:repo-uri (or (System/getenv "LAMBDACD_GIT_TESTREPO_SSH") "git@gitlab.com:flosell-test/testrepo.git")
                             :home-dir (lambdacd-util/create-temp-dir)}
                    :pipeline-structure pipeline-structure)
        (init-ssh :strict-host-key-checking "no")
        (start-pipeline)
        (trigger-manually)
        (wait-for-completion)

        (expect-success)

        (reset-init-ssh))))
