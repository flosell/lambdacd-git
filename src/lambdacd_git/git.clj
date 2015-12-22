(ns lambdacd-git.git
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.lib Ref)))

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
