(ns lambdacd-git.git-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.git-utils :refer [git-init git-add-file git-commit git-checkout-b git-checkout commit-by-msg]]
            [lambdacd-git.git :refer :all]
            [lambdacd.util :as util]
            [lambdacd-git.old-utils :refer [some-ctx]]
            [lambdacd-git.test-utils :refer [str-containing]]
            [clojure.java.io :as io])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib Repository BaseRepositoryBuilder RepositoryBuilder)))

(defn git-from-dir [git-dir]
  (-> (RepositoryBuilder.)
      (.setGitDir (io/file git-dir ".git"))
      (.readEnvironment)
      (.build)
      (Git.)))


(deftest current-revision-test
  (testing "that it can get the head of the master branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))]
      (is (= (:hash (first (:commits git-handle))) (current-revision (:remote git-handle) "master")))))
  (testing "that it can get the head of a different branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit on master")
                         (git-checkout-b "some-branch")
                         (git-commit "some commit on branch")
                         (git-checkout "master"))]
      (is (= (:hash (second (:commits git-handle))) (current-revision (:remote git-handle) "some-branch"))))))

(deftest clone-repo-test
  (testing "that we can clone the head of master"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit on master"))
          workspace (util/create-temp-dir)]
      (clone-repo (:remote git-handle) workspace)
      (is (= "some content"
             (slurp (io/file workspace "some-file")))))))

(deftest checkout-ref-test
  (testing "that we can checkout the head of a branch"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit on master")
                         (git-checkout-b "some-branch")
                         (git-add-file "some-file" "some content on branch")
                         (git-commit "some commit on branch")
                         (git-checkout "master"))
          workspace (:dir git-handle)]
      (checkout-ref (git-from-dir workspace) "some-branch")
      (is (= "some content on branch"
             (slurp (io/file workspace "some-file"))))))
  (testing "that we can checkout any commit"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "first commit")
                         (git-add-file "some-file" "some other content")
                         (git-commit "second commit"))
          workspace (:dir git-handle)
          first-commit-hash (:hash (first (:commits git-handle)))]
      (checkout-ref (git-from-dir workspace) first-commit-hash)
      (is (= "some content"
             (slurp (io/file workspace "some-file")))))))

(deftest commit-details-test
  (testing "that it returns the commits between two hashes excluding the first and including the last"
    (let [git-handle (-> (git-init)
                         (git-commit "first commit")
                         (git-commit "second commit")
                         (git-commit "third commit"))
          first-commit (commit-by-msg git-handle "first commit")
          second-commit (commit-by-msg git-handle "second commit")
          third-commit (commit-by-msg git-handle "third commit")
          workspace (:dir git-handle)]
      (is (= [{:hash second-commit
               :msg  "second commit"}
              {:hash third-commit
               :msg  "third commit"}]
             (commits-between workspace first-commit third-commit))))))