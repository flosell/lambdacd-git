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
                    :commits-by-msg {}
                    :staged-file-content nil}]
    (git git-handle "init")
    git-handle))

(defn git-add-file [git-handle file-name file-content]
  (spit (io/file (:dir git-handle) file-name) file-content)
  (git git-handle "add" "-A")
  (assoc git-handle :staged-file-content file-content))

(defn git-commit [git-handle msg]
  (git git-handle   "commit" "-m" msg "--allow-empty")
  (let [new-hash    (s/trim (git git-handle "rev-parse" "HEAD"))
        commit-desc {:hash new-hash :file-content (:staged-file-content git-handle)}]
    (-> git-handle
        (update :commits #(conj % commit-desc))
        (update :commits-by-msg #(assoc % msg commit-desc))
        (assoc :staged-file-content nil))))

(defn git-checkout-b [git-handle new-branch]
  (git git-handle "checkout" "-b" new-branch)
  git-handle)

(defn git-checkout [git-handle branch]
  (git git-handle "checkout" branch)
  git-handle)

(defn commit-by-msg [git-handle msg]
  (or
    (get-in git-handle [:commits-by-msg msg :hash])
    (throw (Exception. (str "no hash found for " msg)))))