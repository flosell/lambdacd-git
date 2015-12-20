(ns lambdacd-git.git-test
  (:require [clojure.test :refer :all]
            [me.raynes.conch :refer [let-programs]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [lambdacd-git.git :refer :all])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))


(defn git [git-handle & args]
  (let [theargs (concat args [{:dir (:dir git-handle)}])]
    (let-programs [git "/usr/bin/git"]
                  (apply git theargs))))

(defn no-file-attributes []
  (into-array FileAttribute []))

(defn- create-temp-dir []
  (str (Files/createTempDirectory "lambdacd-git" (no-file-attributes))))

(defn- git-init []
  (let [dir (create-temp-dir)
        git-handle {:dir dir
                    :remote (str "file://" dir)
                    :commits []}]
    (git git-handle "init")
    git-handle))

(defn- git-commit [git-handle msg]
  (git git-handle "commit" "-m" msg "--allow-empty")
  (let [new-hash (s/trim (git git-handle "rev-parse" "HEAD"))]
    (update git-handle :commits #(conj % new-hash))))

(deftest get-head-hash-test
  (testing "that it can get the head of the master branch"
    (let [git-handle (-> (git-init)
                         (git-commit "some commit"))]
      (is (= (first (:commits git-handle)) (get-head-hash (:remote git-handle) "master"))))))
