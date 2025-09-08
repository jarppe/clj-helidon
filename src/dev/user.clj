(ns user
  (:require [clojure.tools.namespace.repl :as tnr]
            [clojure.tools.logging :as log]
            [kaocha.repl :as k]))


(defn start [] 
  (log/info "user/start: system starting...")
  "System up")


(defn reset []
  (log/info "user/reset: system reseting...")
  (tnr/refresh :after 'user/start))


(defn run-unit-tests []
  (k/run :unit))


(defn run-all-tests []
  (run-unit-tests))
