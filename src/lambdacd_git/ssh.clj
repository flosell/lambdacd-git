(ns lambdacd-git.ssh
  "Functions to customize handling of SSH connections"
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as io])
  (:import (org.eclipse.jgit.util FS)
           (org.eclipse.jgit.transport JschConfigSessionFactory SshSessionFactory)
           (java.io SequenceInputStream File FileInputStream)
           (com.jcraft.jsch JSch IdentityRepository Identity)
           (clojure.lang SeqEnumeration)
           (java.util Vector Collection)))

(defn- known-hosts-streams [known-hosts-files]
  (->> known-hosts-files
       (map fs/expand-home)
       (filter fs/exists?)
       (map io/file)
       (map (fn [^File f] (FileInputStream. f)))
       (SeqEnumeration.)))

(defn set-known-hosts-customizer [known-hosts-files]
  (fn [^JSch jsch]
    (doto jsch
      (.setKnownHosts (SequenceInputStream. (known-hosts-streams known-hosts-files))))))

(defn set-identity-file-customizer
  "Explicitly set the identity file that will be used for authentication.

   All identities will normally be tried, this setting can allow ensuring a specific GitHub account is used
   with permissions to a private repo."
  [identity-file]
  (fn [^JSch jsch]
    (let [current (.getIdentities (.getIdentityRepository jsch))]
      (doto jsch
        (.setIdentityRepository
          (proxy [IdentityRepository] []
            (getIdentities [] (Vector. ^Collection (filter #(= (fs/expand-home identity-file) (.getName ^Identity %)) current)))))))))

(defn session-factory
  "Creates a SshSessionFactory that JGit can use to create Jsch instances.
  Takes customizer-functions that take a Jsch instances as an argument and modify it as a side-effect"
  ^SshSessionFactory [customizer-fns]
  (proxy [JschConfigSessionFactory] []
    (configure [host session]
      ; just to implement the interface
      )
    (createDefaultJSch [^FS fs]
      (let [jsch (proxy-super createDefaultJSch fs)]
        (doall (map #(% jsch) customizer-fns))
        jsch))))
