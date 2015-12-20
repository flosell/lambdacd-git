(ns lambdacd-git.git
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib Ref)))

(defn- ref->hash [^Ref ref]
  (-> ref
      (.getObjectId)
      (.name)))

(defn get-head-hash [remote branch]
  (-> (Git/lsRemoteRepository)
      (.setHeads true)
      (.setRemote remote)
      (.callAsMap)
      (get "refs/heads/master")
      (ref->hash)))
