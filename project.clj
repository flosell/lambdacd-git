(defproject lambdacd-git "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.eclipse.jgit/org.eclipse.jgit "4.1.1.201511131810-r"]
                 [com.jcraft/jsch.agentproxy.jsch "0.0.8"]
                 [com.jcraft/jsch.agentproxy.usocket-jna "0.0.8"]
                 [com.jcraft/jsch.agentproxy.sshagent "0.0.8"]
                 [me.raynes/conch "0.8.0"]
                 [lambdacd "0.6.1-SNAPSHOT"]]
  :offline? true
  :repositories {"jgit-repository" "https://repo.eclipse.org/content/groups/releases/"}
  :test-paths ["test" "example"]
  :profiles {:dev      {:main         lambdacd-git.example.simple-pipeline
                        :dependencies [[compojure "1.1.8"]
                                       [ring-server "0.3.1"]]}})