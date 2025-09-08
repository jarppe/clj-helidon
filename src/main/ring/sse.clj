(ns ring.sse
  "Protocols and utility functions for SSE support."
  (:refer-clojure :exclude [send])
  (:require [ring.sse.protocols :as p]))


(extend-type clojure.lang.IPersistentMap
  p/SSEListener
  (on-open [m sender]
    (when-let [kv (find m :on-open)] ((val kv) sender))))


(defn send
  "Sends a SSE message"
  [sender sso-message]
  (p/-send sender sso-message))


(defn close
  "Closes SSE response."
  ([sender]
   (p/-close sender)))


(defn sse-response?
  "Returns true if the response contains a SSE emitter."
  [response]
  (contains? response ::listener))


(defn sse-request? [req]
  (-> req :headers (get "accept") (= "text/event-stream")))
