(ns lambdacd-git.test-utils
  (:require [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]
            [lambdacd.util :as utils]))

(defn str-containing [expected-substring output]
  (.contains output expected-substring))


(defn- some-ctx-template []
  (let [config {:home-dir (utils/create-temp-dir)}]
    (-> {:initial-pipeline-state   {} ;; only used to assemble pipeline-state, not in real life
         :step-id                  [42]
         :result-channel           (async/chan (async/dropping-buffer 100))
         :pipeline-state-component nil ;; set later
         :config                   config
         :is-killed                (atom false)
         :_out-acc                 (atom "")}
        (event-bus/initialize-event-bus))
    ))

(defn- add-pipeline-state-component [template]
  (if (nil? (:pipeline-state-component template))
    (assoc template :pipeline-state-component
                    (default-pipeline-state/new-default-pipeline-state template :initial-state-for-testing (:initial-pipeline-state template)))
    template))

(defn some-ctx-with [& args]
  (add-pipeline-state-component
    (apply assoc (some-ctx-template) args)))