(ns lambdacd-git.git-utils
  (:require [me.raynes.conch :refer [let-programs]]
            [clojure.java.io :as io]
            [clojure.string :as s])
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

(defn git-init []
  (let [dir (create-temp-dir)
        git-handle {:dir dir
                    :remote (str "file://" dir)
                    :commits []
                    :commits-by-msg {}}]
    (git git-handle "init")
    git-handle))

(defn git-commit [git-handle msg]
  (git git-handle "commit" "-m" msg "--allow-empty")
  (let [new-hash (s/trim (git git-handle "rev-parse" "HEAD"))]
    (-> git-handle
        (update :commits #(conj % new-hash))
        (update :commits-by-msg #(assoc % msg new-hash)))))

(defn git-checkout-b [git-handle new-branch]
  (git git-handle "checkout" "-b" new-branch)
  git-handle)

(defn git-checkout [git-handle branch]
  (git git-handle "checkout" branch)
  git-handle)