(ns jarppe.clj-helidon.sse-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [matcher-combinators.test] 
            [matcher-combinators.matchers :as m]
            [ring.sse]
            [jarppe.clj-helidon :as server]))


(defn GET [port]
  (with-open [socket (java.net.Socket. "localhost" port)
              out    (-> (.getOutputStream socket) (io/writer))
              in     (-> (.getInputStream socket) (io/reader))]
    (doto out
      (.write "GET / HTTP/1.1\r\n")
      (.write "host: localhost:") (.write (str port)) (.write "\r\n")
      (.write "accept: text/event-stream\r\n")
      (.write "connection: close\r\n")
      (.write "\r\n")
      (.flush))
    (let [status-line  (.readLine in)
          [_ status _] (str/split status-line #"\s+")
          headers      (loop [headers {}]
                         (let [line (.readLine in)]
                           (if (pos? (String/.length line))
                             (let [[_ k v] (re-matches #"([^:]+):\s*(.*)" line)]
                               (recur (assoc headers (str/lower-case k) v)))
                             headers)))
          read-message (fn read-message []
                         (loop [message {}]
                           (let [line (.readLine in)]
                             (if (pos? (.length line))
                               (let [[_ k v] (re-matches #"([^:]+):\s*(.*)" line)]
                                 (recur (assoc message (keyword k) v)))
                               message))))]
      {:status  (parse-long status)
       :headers headers
       :body    (->> (repeatedly read-message)
                     (take-while (fn [message] (-> message :event (not= "close"))))
                     (into []))})))


(deftest sse-test
  (let [handler (fn [_req]
                  {:ring.sse/listener {:on-open (fn [sse]
                                                  (ring.sse/send sse {:id   1
                                                                      :name "test"
                                                                      :data "hello"})
                                                  (ring.sse/send sse {:id   2
                                                                      :name "test"
                                                                      :data "world"})
                                                  (ring.sse/send sse {:name "close"})
                                                  (ring.sse/close sse))}})]
    (with-open [server (server/create-server handler)]
      (is (match? {:status  200
                   :headers {"content-type" "text/event-stream"}
                   :body    (m/via (partial take 2) [{:id    "1"
                                                      :event "test"
                                                      :data  "hello"}
                                                     {:id    "2"
                                                      :event "test"
                                                      :data  "world"}])}
                  (GET (server/port server)))))))
