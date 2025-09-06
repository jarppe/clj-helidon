(ns jarppe.clj-helidon
  (:require [jarppe.clj-helidon.impl :as helidon])
  (:import (io.helidon.webserver WebServer)))


(set! *warn-on-reflection* true)


(defn create-server
  "Create and start an instance of Helidon Web server. Accepts a ring handler and options.
   
   Currently supported options are:
      :host            Host name to use, defaults to \"127.0.0.1\"
      :port            Port to use, or 0 to let server pick any available port number. Defaults to 0
   
   The returned server object implements `java.io.Closeable`, so you can use it with 
   clojure.core/with-open:

   ```
   (with-open [s (create-server ...)]
     ... do something with server
   )
   ```"
  ([handler] (create-server handler nil))
  ([handler opts]
   (helidon/create-server handler opts)))


(defn close [server]
  (when server
    (java.io.Closeable/.close server)))


(defn port [server]
  (-> server :helidon (WebServer/.port)))


(defn running? [server]
  (-> server :helidon (WebServer/.isRunning)))


(defn status [server]
  (if (running? server) :running :stopped))


(defn webserver ^WebServer [server]
  (-> server :helidon))
