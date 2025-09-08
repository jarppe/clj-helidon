(ns jarppe.clj-helidon.sse
  (:require [ring.sse.protocols :as p])
  (:import (io.helidon.webserver.http ServerResponse)
           (io.helidon.webserver.sse SseSink)
           (io.helidon.http.sse SseEvent)))


(set! *warn-on-reflection* true)


(defn- sse-event ^SseEvent [data-or-opts]
  (if (map? data-or-opts)
    (let [builder (SseEvent/builder)]
      (when-let [id (:id data-or-opts)]     (.id builder (str id)))
      (when-let [name (:name data-or-opts)] (.name builder (str name)))
      (.data builder (:data data-or-opts ""))
      (.build builder))
    (SseEvent/create data-or-opts)))


(defrecord HelidonSSESender [^SseSink sse-sink]
  p/SSESender
  (-send [_ message] (.emit sse-sink (sse-event message)))
  (-close [_] (.close sse-sink))
  
  java.io.Closeable
  (close [_] (.close sse-sink)))


(defn handle-sse-resp [ring-req ring-resp]
  (let [on-open (-> ring-resp :ring.sse/listener :on-open)]
    (on-open (-> ring-req
                 :helidon/response
                 (ServerResponse/.sink SseSink/TYPE)
                 (HelidonSSESender.)))))
