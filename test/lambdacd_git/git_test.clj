(ns lambdacd-git.git-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.git-utils :refer [git-init git-commit git-checkout-b git-checkout]]
            [lambdacd-git.git :refer :all]))


(deftest current-revision-test
  (testing "that it can get the head of the master branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))]
      (is (= (first (:commits git-handle)) (current-revision (:remote git-handle) "master")))))
  (testing "that it can get the head of a different branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit on master")
                         (git-checkout-b "some-branch")
                         (git-commit "some commit on branch")
                         (git-checkout "master"))]
      (is (= (second (:commits git-handle)) (current-revision (:remote git-handle) "some-branch"))))))
