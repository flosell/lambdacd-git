(ns lambdacd-git.old-utils
  (:require [lambdacd.util :as utils]
            [lambdacd-git.core :as git-core]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [clojure.string :as s]
            [clojure.core.async :as async]))

(defn some-ctx-template []
  (let [config {:home-dir    (utils/create-temp-dir)}]
    (-> {:initial-pipeline-state   {} ;; only used to assemble pipeline-state, not in real life
         :step-id                  [42]
         :result-channel           (async/chan (async/dropping-buffer 100))
         :pipeline-state-component nil ;; set later
         :config                   config
         :is-killed                (atom false)
         :_out-acc                 (atom "")}
        (event-bus/initialize-event-bus))
    ))

(defn add-pipeline-state-component [template]
  (if (nil? (:pipeline-state-component template))
    (assoc template :pipeline-state-component
                    (default-pipeline-state/new-default-pipeline-state template :initial-state-for-testing (:initial-pipeline-state template)))
    template))

(defn some-ctx []
  (add-pipeline-state-component
    (some-ctx-template)))

(defn some-ctx-with [& args]
  (add-pipeline-state-component
    (apply assoc (some-ctx-template) args)))

(defn get-or-timeout [c & {:keys [timeout]
                           :or   {timeout 10000}}]
  (async/alt!!
    c ([result] result)
    (async/timeout timeout) {:status :timeout}))

(defn git-commits [cwd]
  (reverse (s/split-lines (:out (utils/bash cwd "git log --pretty=format:%H")))))

(defn git-head-commit [cwd]
  (last (git-commits cwd)))

(defn create-test-repo []
  (let [dir (utils/create-temp-dir)]
    (utils/bash dir
                "git init"
                "echo \"hello\" > foo"
                "git add -A"
                "git commit -m \"some message\""
                "echo \"world\" > foo"
                "git add -A"
                "git commit -m \"some other message\"")
    {:dir dir
     :commits (git-commits dir)}))

(defn create-test-repo-with-branch []
  (let [dir (utils/create-temp-dir)]
    (utils/bash dir
                "git init"
                "echo \"hello\" > foo"
                "git add -A"
                "git commit -m \"some message\""
                "git branch develop"
                "git checkout develop"
                "echo \"world\" > bar"
                "git add -A"
                "git commit -m \"some other message\"")
    {:dir dir
     :commits (git-commits dir)}))

(defn commit-to
  ([git-dir]
   (commit-to git-dir "new commit"))
  ([git-dir commit-msg]
   (utils/bash git-dir
               "echo x >> foo"
               "git add -A"
               (str "git commit -m \"" commit-msg "\""))
   (git-head-commit git-dir)))

(defn step-that-returns-the-current-cwd-head [{cwd :cwd} & _]
  {:current-head (git-head-commit cwd)
   :status :success})

(defn repo-uri-for [git-src-dir]
  (str "file://" git-src-dir))

(defn get-value-or-timeout-from [c]
  (get-or-timeout c :timeout 60000))

(defn some-context-with [last-seen-revision result-channel is-killed]
  (some-ctx-with
    :initial-pipeline-state { 9 { [42] { :_git-last-seen-revision last-seen-revision }}
                             10 {}}
    :result-channel result-channel
    :is-killed is-killed))

(defn some-context-with-home [parent]
  (some-ctx-with :config {:home-dir parent}))

(defn execute-wait-for-async
  ([git-src-dir last-seen-revision]
   (execute-wait-for-async git-src-dir last-seen-revision (async/chan 100) (atom false)))
  ([git-src-dir last-seen-revision result-channel is-killed]
   (let [ctx (some-context-with last-seen-revision result-channel is-killed)
         ch (async/go (git-core/wait-for-git ctx (repo-uri-for git-src-dir) "master" :ms-between-polls 100))]
     (Thread/sleep 500) ;; dirty hack to make sure we started waiting before making the next commit
     ch)))
