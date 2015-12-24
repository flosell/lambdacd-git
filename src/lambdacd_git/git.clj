(ns lambdacd-git.git
  (:require [clojure.java.io :as io]
            [lambdacd.steps.support :as support])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib Ref TextProgressMonitor)
           (java.io PrintWriter)))

(defn- ref->hash [^Ref ref]
  (-> ref
      (.getObjectId)
      (.name)))

(defn current-revision [remote branch]
  (-> (Git/lsRemoteRepository)
      (.setHeads true)
      (.setRemote remote)
      (.callAsMap)
      (get (str "refs/heads/" branch))
      (ref->hash)))

(defn- ref-exists? [git ref]
  (-> git
      (.getRepository)
      (.resolve ref)
      (nil?)
      (not)))

(defn ref-or-nil [git ref]
  (if (ref-exists? git ref)
    ref
    nil))

(defn- find-ref [git ref]
  (or
    (ref-or-nil git (str "origin/" ref))
    (ref-or-nil git ref))
  )

(defn- clone-repo [repo cwd]
  (println "Cloning" repo "...")
  (-> (Git/cloneRepository)
      (.setURI repo)
      (.setDirectory (io/file cwd))
      (.setProgressMonitor (TextProgressMonitor. *out*))
      (.call)))

(defn- checkout-ref [^Git git ref]
  (println "Checking out " ref "...")
  (-> git
      (.checkout)
      (.setName ref)
      (.call)))

(defn clone [ctx repo ref cwd]
  (support/capture-output ctx
                          (let [git (clone-repo repo cwd)
                                existing-ref (find-ref git ref)]
                            (if existing-ref
                              (do
                                (checkout-ref git existing-ref)
                                {:status :success})
                              (do
                                (println "Failure: Could not find ref" ref)
                                {:status :failure})))))