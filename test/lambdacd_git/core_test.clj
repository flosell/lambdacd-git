(ns lambdacd-git.core-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.core :refer :all]
            [lambdacd-git.git-utils :as git-utils]
            [clojure.core.async :as async]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [lambdacd-git.test-utils :refer [str-containing some-ctx-with]]
            [lambdacd.core :as lambdacd-core]
            [lambdacd.util :as util]
            [clojure.java.io :as io]
            [lambdacd-git.git :as git]))

(defn init-state []
  (let [is-killed (atom false)
        ctx (some-ctx-with :is-killed is-killed)]
    (pipeline-state/start-pipeline-state-updater (:pipeline-state-component ctx) ctx)
    (atom {:ctx ctx
           :is-killed is-killed})))

(defn git-init [state]
  (swap! state #(assoc % :git (git-utils/git-init)))
  state)

(defn set-git-remote [state remote]
  (swap! state #(assoc % :git {:remote remote}))
  state)

(defn git-commit [state msg]
  (swap! state #(assoc % :git
                         (git-utils/git-commit (:git %) msg)))
  state)

(defn git-checkout-b [state new-ref]
  (swap! state #(assoc % :git
                         (git-utils/git-checkout-b (:git %) new-ref)))
  state)

(defn git-add-file [state file-name file-content]
  (swap! state #(assoc % :git
                         (git-utils/git-add-file (:git %) file-name file-content)))
  state)

(defn start-wait-for-git-step-with-ref [state ref]
  (let [wait-for-result-channel (async/go
                                  (let [execute-step-result (lambdacd-core/execute-step {} (:ctx @state)
                                                                                        (fn [args ctx]
                                                                                          (wait-for-git ctx (get-in @state [:git :remote]) :ref ref :ms-between-polls 100)))]
                                    (first (vals (:outputs execute-step-result)))))]
    (swap! state #(assoc % :result-channel wait-for-result-channel))
    state))
(defn start-wait-for-git-step [state]
  (let [wait-for-result-channel (async/go
                                  (let [execute-step-result (lambdacd-core/execute-step {} (:ctx @state)
                                                                                        (fn [args ctx]
                                                                                          (wait-for-git ctx (get-in @state [:git :remote]) :ms-between-polls 100)))]
                                    (first (vals (:outputs execute-step-result)))))]
    (swap! state #(assoc % :result-channel wait-for-result-channel))
    state))

(defn start-clone-step [state ref cwd]
  (let [wait-for-result-channel (async/go
                                  (let [execute-step-result (lambdacd-core/execute-step {} (:ctx @state)
                                                                                        (fn [args ctx]
                                                                                          (clone ctx (get-in @state [:git :remote]) ref cwd)))]
                                    (first (vals (:outputs execute-step-result)))))]
    (swap! state #(assoc % :result-channel wait-for-result-channel))
    state))

(defn start-list-changes-step [state cwd old-revision new-revision]
  (let [wait-for-result-channel (async/go
                                  (let [execute-step-result (lambdacd-core/execute-step {} (:ctx @state)
                                                                                        (fn [args ctx]
                                                                                          (list-changes {:cwd             cwd
                                                                                                            :old-revision old-revision
                                                                                                            :revision     new-revision} ctx)))]
                                    (first (vals (:outputs execute-step-result)))))]
    (swap! state #(assoc % :result-channel wait-for-result-channel))
    state))

(defn read-channel-or-time-out [c & {:keys [timeout]
                           :or             {timeout 10000}}]
  (async/alt!!
    c ([result] result)
    (async/timeout timeout) (throw (Exception. "timeout!"))))

(defn kill-waiting-step [state]
  (reset! (:is-killed @state) true)
  state)

(defn get-step-result [state]
  (try
    (let [result (read-channel-or-time-out (:result-channel @state))]
      (swap! state #(assoc % :step-result result))
      state)
    (catch Exception e
      (kill-waiting-step state)
      (throw e))))

(defn wait-for-step-to-complete [state]
  ; just an alias
  (get-step-result state))

(defn wait-a-bit [state]
  (Thread/sleep 500)
  state)

(defn commit-hash-by-msg [state msg]
  (git-utils/commit-by-msg (:git @state) msg))

(defn remote [state]
  (get-in @state [:git :remote]))

(defn step-result [state]
  (:step-result @state))

(deftest wait-for-git-test-clean
  (testing "that it waits for a new commit to happen and that it prints out information on old and new commit"
    (let [state (-> (init-state)
                    (git-init)
                    (git-commit "initial commit")
                    (start-wait-for-git-step-with-ref "refs/heads/master")
                    (wait-a-bit)
                    (git-commit "other commit")
                    (get-step-result))]
      (is (= :success (:status (step-result state))))
      (is (= "refs/heads/master" (:changed-ref (step-result state))))
      (is (= (remote state) (:changed-remote (step-result state))))
      (is (= (commit-hash-by-msg state "initial commit") (:old-revision (step-result state))))
      (is (= (commit-hash-by-msg state "other commit") (:revision (step-result state))))
      (is (str-containing (commit-hash-by-msg state "initial commit") (:out (step-result state))))
      (is (str-containing "on refs/heads/master" (:out (step-result state))))))
  (testing "that we can pass a function to filter refs we want to react on"
    (let [state (-> (init-state)
                    (git-init)
                    (git-commit "initial commit")
                    (git-checkout-b "some-branch")
                    (start-wait-for-git-step-with-ref (fn [ref] (.endsWith ref "some-branch")))
                    (git-commit "other commit")
                    (get-step-result))]
      (is (= :success (:status (step-result state))))
      (is (= (commit-hash-by-msg state "initial commit") (:old-revision (step-result state))))
      (is (= (commit-hash-by-msg state "other commit") (:revision (step-result state))))
      (is (str-containing (commit-hash-by-msg state "other commit") (:out (step-result state))))))
  (testing "that we can pass a regex to filter refes we want to react on"
    (let [state (-> (init-state)
                    (git-init)
                    (git-commit "initial commit")
                    (git-checkout-b "some-branch")
                    (start-wait-for-git-step-with-ref #"refs/heads/some-.*")
                    (git-commit "other commit")
                    (get-step-result))]
      (is (= :success (:status (step-result state))))
      (is (= (commit-hash-by-msg state "initial commit") (:old-revision (step-result state))))
      (is (= (commit-hash-by-msg state "other commit") (:revision (step-result state))))
      (is (str-containing (commit-hash-by-msg state "other commit") (:out (step-result state))))))
  (testing "that we can pass a function that allows all refs"
    (let [state (-> (init-state)
                    (git-init)
                    (start-wait-for-git-step-with-ref (fn [ref] true))
                    (git-commit "initial commit")
                    (wait-for-step-to-complete)
                    (start-wait-for-git-step-with-ref (fn [ref] true))
                    (git-checkout-b "some-branch")
                    (git-commit "other commit")
                    (get-step-result))]
      (is (= :success (:status (step-result state))))
      (is (= nil (:old-revision (step-result state))))
      (is (= (commit-hash-by-msg state "other commit") (:revision (step-result state))))
      (is (str-containing (commit-hash-by-msg state "other commit") (:out (step-result state))))))
  (testing "that it prints out information on old and new commit hashes"
    (let [state (-> (init-state)
                    (git-init)
                    (git-commit "initial commit")
                    (start-wait-for-git-step)
                    (wait-a-bit)
                    (git-commit "other commit")
                    (get-step-result))]
      (is (str-containing (commit-hash-by-msg state "initial commit") (:out (step-result state))))
      (is (str-containing (commit-hash-by-msg state "other commit") (:out (step-result state))))))
  (testing "that waiting returns immediately when a commit happened while it was not waiting"
    (let [state (-> (init-state)
                    (git-init)
                    (git-commit "initial commit")
                    (start-wait-for-git-step)
                    (wait-a-bit)
                    (git-commit "other commit")
                    (wait-for-step-to-complete)
                    (git-commit "commit while not waiting")
                    (start-wait-for-git-step)
                    (get-step-result))]
      (is (= :success (:status (step-result state))))
      (is (= (commit-hash-by-msg state "other commit") (:old-revision (step-result state))))
      (is (= (commit-hash-by-msg state "commit while not waiting") (:revision (step-result state))))))
  (testing "that wait-for can be killed and that the last seen revision is being kept"
    (let [state (-> (init-state)
                    (git-init)
                    (git-commit "initial commit")
                    (start-wait-for-git-step)
                    (wait-a-bit)
                    (kill-waiting-step)
                    (get-step-result))]
      (is (= :killed (:status (step-result state))))
      (is (= {"refs/heads/master" (commit-hash-by-msg state "initial commit")} (:_git-last-seen-revisions (step-result state))))))
  (testing "that it retries until being killed if the repository cannot be reached"
    (let [state (-> (init-state)
                    (set-git-remote "some-uri-that-doesnt-exist")
                    (start-wait-for-git-step)
                    (wait-a-bit)
                    (kill-waiting-step)
                    (get-step-result))]
      (is (= :killed (:status (step-result state))))))
  (testing "that it prints out errors if a repository can't be reached"
    (let [state (-> (init-state)
                    (set-git-remote "some-uri-that-doesnt-exist")
                    (start-wait-for-git-step)
                    (wait-a-bit)
                    (kill-waiting-step)
                    (get-step-result))]
      (is (str-containing "some-uri-that-doesnt-exist" (:out (step-result state))))))
  (testing "that it assumes master if no ref is given"
    (let [state (-> (init-state)
                    (git-init)
                    (start-wait-for-git-step)
                    (git-commit "initial commit")
                    (get-step-result))]
      (is (= :success (:status (step-result state))))
      (is (= (commit-hash-by-msg state "initial commit") (:revision (step-result state)))))))
(deftest clone-test
  (testing "that we can clone a specific commit"
    (let [state (init-state)
          workspace (util/create-temp-dir)]
      (-> state
          (git-init)
          (git-add-file "some-file" "some content")
          (git-commit "first commit")
          (git-add-file "some-file" "some other content")
          (git-commit "second commit")
          (start-clone-step (commit-hash-by-msg state "first commit") workspace)
          (get-step-result))
      (is (= :success (:status (step-result state))))
      (is (= "some content"
             (slurp (io/file workspace "some-file"))))))
  (testing "that we can get information on the progress of a clone"
    (let [state (init-state)
          workspace (util/create-temp-dir)]
      (-> state
          (git-init)
          (git-add-file "some-file" "some content")
          (git-commit "some commit")
          (start-clone-step (commit-hash-by-msg state "some commit") workspace)
          (get-step-result))
      (is (str-containing "Receiving" (:out (step-result state))))))
  (testing "that we get a proper error if a commit cant be found"
    (let [state (init-state)
          workspace (util/create-temp-dir)]
      (-> state
          (git-init)
          (git-add-file "some-file" "some content")
          (git-commit "some commit")
          (start-clone-step "some-branch" workspace)
          (get-step-result))
      (is (= :failure (:status (step-result state))))
      (is (str-containing "Could not find ref some-branch" (:out (step-result state)))))))

(deftest list-changes-test
  (testing "normal behavior"
    (let [state (init-state)
          workspace (util/create-temp-dir)]
      (-> state
          (git-init)
          (git-commit "first commit")
          (git-commit "second commit")
          (git-commit "third commit")
          (start-clone-step "master" workspace)
          (wait-for-step-to-complete)
          (start-list-changes-step workspace (commit-hash-by-msg state "first commit") (commit-hash-by-msg state "third commit"))
          (get-step-result))
      (testing "that it returns the changed commits"
        (is (= [{:hash (commit-hash-by-msg state "second commit")
                 :msg  "second commit"}
                {:hash (commit-hash-by-msg state "third commit")
                 :msg  "third commit"}] (:commits (step-result state)))))
      (testing "that it is successful"
        (is (= :success
               (:status (step-result state)))))
      (testing "that it outputs the commits messages"
        (is (str-containing "second commit" (:out (step-result state))))
        (is (str-containing "third commit" (:out (step-result state)))))
      (testing "that it outputs the commit hashes"
        (is (str-containing (commit-hash-by-msg state "second commit") (:out (step-result state))))
        (is (str-containing (commit-hash-by-msg state "third commit") (:out (step-result state)))))))
  (testing "error handling"
    (testing "that an error is reported if no cwd is set"
      (let [state (init-state)]
        (-> state
            (start-list-changes-step nil "some hash" "some other hash")
            (get-step-result))
        (is (str-containing "No working directory" (:out (step-result state))))
        (is (= :failure (:status (step-result state))))))
    (testing "that an error is reported if no git repo is found in cwd"
      (let [state (init-state)
            workspace (util/create-temp-dir)]
        (-> state
            (start-list-changes-step workspace "some hash" "some other hash")
            (get-step-result))
        (is (str-containing "No .git directory" (:out (step-result state))))
        (is (= :failure (:status (step-result state))))))
    (testing "that the current head commit will be reported if no old and new revisions are set"
      (let [state (init-state)
            workspace (util/create-temp-dir)]
        (-> state
            (git-init)
            (git-commit "some commit")
            (start-clone-step "HEAD" workspace)
            (wait-for-step-to-complete)
            (start-list-changes-step workspace nil nil)
            (get-step-result))
        (is (str-containing "Current HEAD" (:out (step-result state))))
        (is (str-containing (commit-hash-by-msg state "some commit") (:out (step-result state))))
        (is (= :success (:status (step-result state)))))
      )))