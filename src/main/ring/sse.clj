(ns ring.sse
  "Protocols and utility functions for SSE support."
  (:refer-clojure :exclude [send])
  (:require [ring.sse.protocols :as p]))


(extend-type clojure.lang.IPersistentMap
  p/SSEListener
  (on-open [m emitter]
    (when-let [kv (find m :on-open)] ((val kv) emitter))))


(defn send
  "Sends a SSE message"
  [emitter sso-message]
  (p/-send emitter sso-message))


(defn close
  "Closes SSE response."
  ([emitter]
   (p/-close emitter)))


(defn sse-response?
  "Returns true if the response contains a SSE emitter."
  [response]
  (contains? response ::listener))
