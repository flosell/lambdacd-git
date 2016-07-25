(ns lambdacd-git.git-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.git-utils :refer [git-init git-add-file git-commit git-checkout-b git-checkout commit-by-msg
                                            git-tag git-tag-list git-user-name git-user-email commit-timestamp-date]]
            [lambdacd-git.git :refer :all]
            [lambdacd.util :as util]
            [lambdacd-git.test-utils :refer [str-containing]]
            [clojure.java.io :as io])
  (:import (org.eclipse.jgit.api Git)))

(defn git-from-dir [git-dir]
  (Git/open (io/file git-dir)))

(defn no-branches []
  (constantly false))

(defn match-branch [branch]
  (match-ref (str "refs/heads/" branch)))

(defn match-tag [tag]
  (match-ref (str "refs/tags/" tag)))

(defn match-all-refs []
  (constantly true))

(deftest current-revision-test
  (testing "that it can get the head of the master branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))]
      (is (= {"refs/heads/master" (commit-by-msg git-handle "some commit")}
             (current-revisions (:remote git-handle) (match-branch "master"))))))
  (testing "that it can get the head of all branches"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit on master")
                         (git-checkout-b "some-branch")
                         (git-commit "some commit on branch")
                         (git-checkout "master"))]
      (is (= {"refs/heads/some-branch" (commit-by-msg git-handle "some commit on branch")
              "refs/heads/master"      (commit-by-msg git-handle "some commit on master")}
             (current-revisions (:remote git-handle) (match-all-refs))))))
  (testing "that it returns an emtpy map if no ref matches"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit on master"))]
      (is (= {}
             (current-revisions (:remote git-handle) (no-branches))))))
  (testing "that it can get the head of tags"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit on master")
                         (git-tag "some-tag"))]
      (is (= {"refs/heads/master"  (commit-by-msg git-handle "some commit on master")
              "refs/tags/some-tag" (commit-by-msg git-handle "some commit on master")}
             (current-revisions (:remote git-handle) (match-all-refs))))
      (is (= {"refs/tags/some-tag" (commit-by-msg git-handle "some commit on master")}
             (current-revisions (:remote git-handle) (match-tag "some-tag")))))))

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
  (testing "that we can checkout a tag"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit")
                         (git-tag "some-tag")
                         (git-add-file "some-file" "some other content")
                         (git-commit "some other commit"))
          workspace (:dir git-handle)]
      (checkout-ref (git-from-dir workspace) "some-tag")
      (is (= "some content"
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

(defn- expected-author [git-handle]
  (str (git-user-name git-handle) " <" (git-user-email git-handle) ">"))

(defn expected-timestamp [git-handle commit-msg]
  (commit-timestamp-date git-handle (commit-by-msg git-handle commit-msg)))

(deftest commits-between-test
  (testing "that it returns the commits between two hashes excluding the first and including the last"
    (let [git-handle (-> (git-init)
                         (git-commit "first commit")
                         (git-commit "second commit")
                         (git-commit "third commit"))
          first-commit (commit-by-msg git-handle "first commit")
          second-commit (commit-by-msg git-handle "second commit")
          third-commit (commit-by-msg git-handle "third commit")
          workspace (:dir git-handle)]
      (is (= [{:hash      second-commit
               :msg       "second commit"
               :author    (expected-author git-handle)
               :timestamp (expected-timestamp git-handle "second commit")}
              {:hash      third-commit
               :msg       "third commit"
               :author    (expected-author git-handle)
               :timestamp (expected-timestamp git-handle "third commit")}]
             (commits-between workspace first-commit third-commit))))))

(deftest get-single-commit-test
  (testing "that we can get commit information for a specific commit"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))]
      (is (= {:hash      (commit-by-msg git-handle "some commit")
              :msg       "some commit"
              :author    (expected-author git-handle)
              :timestamp (expected-timestamp git-handle "some commit")}
             (get-single-commit (:dir git-handle) (commit-by-msg git-handle "some commit"))))))
  (testing "that we can get commit information for the HEAD commit"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))]
      (is (= {:hash      (commit-by-msg git-handle "some commit")
              :msg       "some commit"
              :author    (expected-author git-handle)
              :timestamp (expected-timestamp git-handle "some commit")}
             (get-single-commit (:dir git-handle) "HEAD"))))))

(deftest tag-revision-test
  (testing "that we can tag the head of the master branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))
          workspace (:dir git-handle)]
      (tag-revision workspace "HEAD" "some-tag")
      (is (= "some-tag\n"
             (git-tag-list git-handle "HEAD")))))
  (testing "that we can tag some commit on another branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit on master")
                         (git-checkout-b "some-branch")
                         (git-commit "some commit on branch")
                         (git-commit "some other commit on branch")
                         (git-checkout "master"))
          commit (commit-by-msg git-handle "some commit on branch")
          workspace (:dir git-handle)]
      (tag-revision workspace commit "some-tag")
      (is (= "some-tag\n"
             (git-tag-list git-handle commit))))))
