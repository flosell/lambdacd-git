(ns lambdacd-git.test-utils)

(defn str-containing [expected-substring output]
  (.contains output expected-substring))