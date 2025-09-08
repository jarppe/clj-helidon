(ns jarppe.clj-helidon.sse-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [matcher-combinators.test] 
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
    (let [[status-line & more] (line-seq in)
          [_ status _]         (str/split status-line #"\s+")
          [headers more]       (loop [headers       {}
                                      [line & more] more]
                                 (if (pos? (String/.length line))
                                   (let [[_ k v] (re-matches #"([^:]+):\s*(.*)" line)]
                                     (recur (assoc headers (str/lower-case k) v)
                                            more))
                                   [headers more]))]
      {:status  (parse-long status)
       :headers headers
       :body    (loop [message       {}
                       messages      []
                       [line & more] more]
                  (if line
                    (if (zero? (String/.length line))
                      (recur {} (conj messages message) more)
                      (let [[_ k v] (re-matches #"([^:]+):\s*(.*)" line)]
                        (recur (assoc message (keyword k) v)
                               messages
                               more)))
                    messages))})))


(deftest sse-test
  (let [handler (fn [_req]
                  {:ring.sse/listener {:on-open (fn [sse]
                                                  (ring.sse/send sse {:id           1
                                                                      :content-type "text/plain"
                                                                      :name         "test"
                                                                      :data         "hello"})
                                                  (ring.sse/close sse))}})]
    (with-open [server (server/create-server handler)]
      (is (match? {:status  200
                   :headers {"content-type" "text/event-stream"}
                   :body    [{:id    "1"
                              :event "test"
                              :data  "hello"}]}
                  (GET (server/port server)))))))
