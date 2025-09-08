(ns jarppe.clj-helidon.sse-test-server
  (:require [ring.sse :as sse]
            [jarppe.clj-helidon :as server]))


(defn index-handler [req]
  (when (and (= (req :uri) "/")
             (not (ring.sse/sse-request? req)))
    {:status  200
     :headers {"content-type" "text/html; charset=utf-8"}
     :body    "<!DOCTYPE html> 
                 <html> 
                   <body> 
                     <h1>SSE test</h1> 
                     <button id=\"start\">Start</button> 
                     <button id=\"stop\">Stop</button> 
                     <ul id=\"messages\"> 
                     </ul> 
                   </body> 
                   <script> 
                     
                     const newMessage = (m) => {
                       console.log(\"newMessage:\", m)
                       const message = document.createElement(\"li\")
                       message.appendChild(document.createTextNode(m.data))
                       document.getElementById(\"messages\").appendChild(message)
                     }
                     
                     let source = null
               
                     const stop = () => {
                       if (source) {
                         source.close()
                         source = null
                       }
                     }

                     const start = () => {
                       stop()                       
                       source = new EventSource(\"\") 
                       source.addEventListener(\"test\", newMessage)
                       source.addEventListener(\"close\", () => console.log(\"source closed\"))
                       source.addEventListener(\"error\", (e) => console.log(\"source error:\", e))
                     }

                     document.getElementById(\"start\").addEventListener(\"click\", start)
                     document.getElementById(\"stop\").addEventListener(\"click\", stop)

                   </script> 
                 </html>"}))


(defn sse-on-open [sse last-event-id]
  (doseq [[id m] (map vector
                      (map (partial + (inc last-event-id)) (range))
                      (cycle ["hullo" "sse" "world" "!"]))]
    (ring.sse/send sse {:id   id
                        :name "test"
                        :data (str id ": " m)})
    (Thread/sleep 500)))


(defn sse-handler [req]
  (when (and (= (req :uri) "/")
             (ring.sse/sse-request? req))
    (let [last-event-id (-> req :headers (get "last-event-id") (or "0") (parse-long))]
      {:ring.sse/listener {:on-open (fn [sse] (sse-on-open sse last-event-id))}})))


(defn not-found-handler [_req]
  {:status  404
   :headers {"content-type" "text/plain"}
   :body    "Say what?"})


(defn error-logger [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (println "error:" (-> e (.getClass) (.getName)) (-> e (.getMessage)))
        {:status 500
         :headers {"content-type" "text/plain; charset=utf-8"}
         :body   "Ups, my bad!"}))))


(defonce server (atom nil)) 


(swap! server (fn [server]
                (when server (.close server))
                (server/create-server (-> (some-fn index-handler
                                                   sse-handler
                                                   not-found-handler)
                                          (error-logger))
                                      {:port 8888})))


(comment
  (.close @server)
  )