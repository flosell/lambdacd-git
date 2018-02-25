(ns lambdacd-git.ssh
  "Functions to customize handling of SSH connections"
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [lambdacd-git.ssh-agent-support :as ssh-agent-support])
  (:import (org.eclipse.jgit.util FS)
           (org.eclipse.jgit.transport JschConfigSessionFactory SshSessionFactory OpenSshConfig$Host)
           (java.io SequenceInputStream File FileInputStream ByteArrayInputStream)
           (com.jcraft.jsch JSch JSchException IdentityRepository Identity Session)
           (clojure.lang SeqEnumeration)
           (java.util Vector Collection)))

(defn- empty-input-stream-as-fallback []
  (ByteArrayInputStream. (byte-array 0)))

(defn- known-hosts-streams [known-hosts-files]
  (->> known-hosts-files
       (map fs/expand-home)
       (filter fs/exists?)
       (map io/file)
       (map (fn [^File f] (FileInputStream. f)))
       (cons (empty-input-stream-as-fallback))
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
    (let [identity-file-full-path (str (fs/expand-home identity-file))]
      (try
        (.addIdentity jsch identity-file-full-path)
        (catch JSchException e :ignore))
      (let [current (.getIdentities (.getIdentityRepository jsch))]
        (doto jsch
          (.setIdentityRepository
           (proxy [IdentityRepository] []
             (getIdentities [] (Vector. ^Collection (filter #(= identity-file-full-path (.getName ^Identity %)) current))))))))))

(defn set-strict-host-key-checking-customizer
  "Explicitly set StrictHostKeyChecking parameter, overriding normal SSH configuration"
  [setting]
  (fn [^Session session]
    (.setConfig session "StrictHostKeyChecking" setting)))

(defn session-factory
  "Creates a SshSessionFactory that JGit can use to create Jsch instances.
  Takes customizer-functions that take a Jsch instances as an argument and modify it as a side-effect"
  ^SshSessionFactory
  ([jsch-customizer-fns] ; DEPRECATAED
   (session-factory jsch-customizer-fns []))
  ([jsch-customizer-fns session-customizer-fns]
  (proxy [JschConfigSessionFactory] []
    (configure [^OpenSshConfig$Host host ^Session session]
      (doall (map #(% session) session-customizer-fns)))
    (createDefaultJSch [^FS fs]
      (let [jsch (proxy-super createDefaultJSch fs)]
        (doall (map #(% jsch) jsch-customizer-fns))
        jsch)))))

(defn session-factory-for-config [{:keys [use-agent known-hosts-files identity-file strict-host-key-checking]
                                   :or   {use-agent         true
                                          known-hosts-files ["~/.ssh/known_hosts" "/etc/ssh/ssh_known_hosts"]}}]
  (let [jsch-customizer-fns    (filter some? [(when use-agent ssh-agent-support/ssh-agent-customizer)
                                              (when known-hosts-files (set-known-hosts-customizer known-hosts-files))
                                              (when identity-file (set-identity-file-customizer identity-file))])
        session-customizer-fns (filter some? [(when strict-host-key-checking (set-strict-host-key-checking-customizer strict-host-key-checking))])]
    (session-factory jsch-customizer-fns
                     session-customizer-fns)))

(def init-ssh-called? (atom false))
