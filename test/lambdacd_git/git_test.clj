(ns lambdacd-git.git-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.git-utils :refer [git-init git-add-file git-commit git-checkout-b git-checkout commit-by-msg]]
            [lambdacd-git.git :refer :all]
            [lambdacd.util :as util]
            [lambdacd-git.old-utils :refer [some-ctx]]
            [lambdacd-git.test-utils :refer [str-containing]]
            [clojure.java.io :as io]))


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

(deftest clone-test
  (testing "that we can clone the head of master"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit on master"))
          workspace (util/create-temp-dir)]
      (clone (some-ctx) (:remote git-handle) "master" workspace)
      (is (= "some content"
             (slurp (io/file workspace "some-file"))))))
  (testing "that we get information on the progress of a clone"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit on master"))
          workspace (util/create-temp-dir)]
      (is (str-containing "Receiving" (:out (clone (some-ctx) (:remote git-handle) "master" workspace))))))
  (testing "that we can clone the head of a branch"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit on master")
                         (git-checkout-b "some-branch")
                         (git-add-file "some-file" "some content on branch")
                         (git-commit "some commit on branch")
                         (git-checkout "master"))
          workspace (util/create-temp-dir)]
      (is (= :success (:status (clone (some-ctx) (:remote git-handle) "some-branch" workspace))))
      (is (= "some content on branch"
             (slurp (io/file workspace "some-file"))))))
  (testing "that we can clone any commit"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "first commit")
                         (git-add-file "some-file" "some other content")
                         (git-commit "second commit"))
          workspace (util/create-temp-dir)
          first-commit-hash (:hash (first (:commits git-handle)))]
      (is (= :success (:status (clone (some-ctx) (:remote git-handle) first-commit-hash workspace))))
      (is (= "some content"
             (slurp (io/file workspace "some-file"))))))
  (testing "that we get a proper error if a commit cant be found"
    (let [git-handle (-> (git-init)
                         (git-add-file "some-file" "some content")
                         (git-commit "some commit on master"))
          workspace (util/create-temp-dir)
          clone-result (clone (some-ctx)(:remote git-handle) "some-ref" workspace)]
      (is (= :failure (:status clone-result)))
      (is (str-containing "Could not find ref some-ref" (:out clone-result)))
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