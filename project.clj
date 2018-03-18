(def lambdacd-version (or
                        (System/getenv "LAMBDACD_VERSION")
                        "0.13.5"))

(def clojure-version-to-use (or
                              (System/getenv "CLOJURE_VERSION")
                              "1.7.0"))

(println "Building against LambdaCD version" lambdacd-version "and clojure" clojure-version-to-use)

(defproject lambdacd-git "0.4.1-SNAPSHOT"
  :description "Git support for LambdaCD"
  :url "https://github.com/flosell/lambdacd-git"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure ~clojure-version-to-use]
                 [org.eclipse.jgit/org.eclipse.jgit "4.1.1.201511131810-r"]
                 [com.jcraft/jsch.agentproxy.jsch "0.0.8"]
                 [com.jcraft/jsch.agentproxy.usocket-jna "0.0.8"]
                 [com.jcraft/jsch.agentproxy.sshagent "0.0.8"]
                 [me.raynes/conch "0.8.0"]
                 [lambdacd ~lambdacd-version]
                 [ring/ring-core "1.6.3"]]
  :repositories {"jgit-repository" "https://repo.eclipse.org/content/groups/releases/"}
  :deploy-repositories [["clojars" {:creds :gpg}]
                        ["releases" :clojars]]
  :test-paths ["test" "example"]
  :test-selectors {:default (constantly true)
                   :skip-e2e-with-auth (complement :e2e-with-auth)}
  :profiles {:dev      {:main         lambdacd-git.example.simple-pipeline
                        :dependencies [[compojure "1.6.0"]
                                       [ring-server "0.4.0"]
                                       [ring/ring-mock "0.2.0"]]}
             :silent   {:jvm-opts ["-Dlogback.configurationFile=./dev-resources/logback-silent.xml"]}})
