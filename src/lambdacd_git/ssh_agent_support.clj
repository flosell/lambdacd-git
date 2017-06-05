(ns lambdacd-git.ssh-agent-support
  (:require [clojure.tools.logging :as log]
            [lambdacd-git.ssh :as ssh])
  (:import (org.eclipse.jgit.transport SshSessionFactory JschConfigSessionFactory)
           (com.jcraft.jsch.agentproxy.connector SSHAgentConnector)
           (org.eclipse.jgit.util FS)
           (com.jcraft.jsch.agentproxy RemoteIdentityRepository)
           (com.jcraft.jsch.agentproxy.usocket JNAUSocketFactory)
           (com.jcraft.jsch JSch)))

(defn add-ssh-agent-connector [^JSch jsch]
  (let [usf   (JNAUSocketFactory.)
        con   (SSHAgentConnector. usf)
        irepo (RemoteIdentityRepository. con)]
    (.setIdentityRepository jsch irepo)))

(defn ssh-agent-customizer [jsch]
  (try
    (if (SSHAgentConnector/isConnectorAvailable)
      (add-ssh-agent-connector jsch)
      (log/info "No SSH-Agent connector available. SSH-Keys with passphrases will not be supported"))
    (catch Exception e
      (log/warn e "Problems with SSH Agent. Falling back to default behavior"))))


; INTERNAL: WILL BE MOVED TO ssh-init
(defn session-factory [customizer-fns]
  (ssh/session-factory customizer-fns))

; DEPRECATED: USE lambdacd-git.ssh-init/init-ssh! instead!
(defn initialize-ssh-agent-support! []
  (SshSessionFactory/setInstance (ssh/session-factory [ssh-agent-customizer])))
