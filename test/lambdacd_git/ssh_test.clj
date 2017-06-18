(ns lambdacd-git.ssh-test
  (:require [clojure.test :refer :all]
            [lambdacd-git.ssh :refer :all]
            [me.raynes.fs :as fs])
  (:import (com.jcraft.jsch JSch Session OpenSSHConfig)
           (org.eclipse.jgit.transport SshSessionFactory URIish CredentialsProvider OpenSshConfig$Host)
           (org.eclipse.jgit.util FS)))

(def some-known-hosts-file-content
  "github.com ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXy28G3skua2SmVi/w4yCE6gbODqnTWlg7+wC604ydGXA8VJiS5ap43JXiUFFAaQ==")

(deftest known-hosts-customizer-test
  (testing "that it can read a known-hosts file"
    (let [known-hosts-file (fs/temp-file "known_hosts")
          customizer       (set-known-hosts-customizer [known-hosts-file])
          jsch             (JSch.)]
      (spit known-hosts-file some-known-hosts-file-content)
      (customizer jsch)
      (is (= 1 (count (.getHostKey (.getHostKeyRepository jsch) "github.com" "ssh-rsa"))))))
  (testing "that it can deal with files that do not exist"
    (let [customizer (set-known-hosts-customizer ["i-do-not-exist"])
          jsch       (JSch.)]
      (customizer jsch)
      (is (= 0 (count (.getHostKey (.getHostKeyRepository jsch))))))))


(deftest session-factory-for-config-test
  (testing "StrictHostKeyChecking"
    (testing "that it sets the config on the session if requested"
      (let [session-factory (session-factory-for-config {:strict-host-key-checking "SomeSetting"})
            session         (.getSession (JSch.) "someHost")]
        (.configure session-factory (OpenSshConfig$Host.) session)
        (is (= "SomeSetting" (.getConfig session "StrictHostKeyChecking")))))))
