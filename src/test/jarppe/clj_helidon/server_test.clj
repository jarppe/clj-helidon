(ns jarppe.clj-helidon.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [clj-http.client :as client]
            [jarppe.clj-helidon :as server])
  (:import (java.io ByteArrayInputStream)
           (java.nio.charset StandardCharsets)))



(deftest server-started-with-defaults
  (let [handler (fn [_req] {:status  200
                            :headers {"content-type" "text-plain"}
                            :body    "hello"})]
    (with-open [server (server/create-server handler)]
      (is (instance? io.helidon.webserver.WebServer (server/webserver server)))
      (is (pos? (server/port server)))
      (is (true? (server/running? server)))
      (is (= :running (server/status server)))
      (server/close server)
      (is (false? (server/running? server)))
      (is (= :stopped (server/status server))))))


(deftest server-request-is-correct
  (let [req     (atom nil)
        handler (fn [req']
                  (reset! req req')
                  {:status  200
                   :headers {"content-type" "text-plain"
                             "x-resp-id"    "xyz"}
                   :body    "server response data"})]
    (with-open [server (server/create-server handler)]
      (let [resp (client/post (str "http://localhost:" (server/port server) "/foo")
                              {:query-params {:a "b"
                                              :c "d"}
                               :headers      {"x-apikey"     "1234"
                                              "content-type" "text/plain"}
                               :body         "client post data"})]
        (testing "server response is correct"
          (is (match? {:status  200
                       :headers {"x-resp-id"    "xyz"
                                 "Content-Type" "text-plain"}
                       :body    "server response data"}
                      resp)))))
    (testing "server request was correct"
      (is (match? {:request-method :post
                   :server-port    integer?
                   :server-name    "127.0.0.1"
                   :remote-addr    "127.0.0.1"
                   :scheme         :http
                   :uri            "/foo"
                   :query-string   "a=b&c=d"
                   :headers        {"x-apikey"     "1234"
                                    "content-type" "text/plain"}
                   :body           (m/via slurp "client post data")}
                  @req)))))


(defn run-response-type-test [body]
  (with-open [server (server/create-server (fn [_]
                                             {:status  200
                                              :headers {"content-type" "text-plain; charset=UTF-8"}
                                              :body    body}))]
    (client/get (str "http://localhost:" (server/port server) "/"))))


(deftest response-types-test
  (testing "string body"
    (let [body "hello"
          resp (run-response-type-test body)]
      (is (match? {:status  200
                   :body    body
                   :headers {"content-type" "text-plain; charset=UTF-8"}}
                  resp)))) 
  (testing "string byte[]"
    (let [body          "hello"
          body-as-array (.getBytes body StandardCharsets/UTF_8)
          resp          (run-response-type-test body-as-array)]
      (is (match? {:status  200
                   :body    body
                   :headers {"content-type" "text-plain; charset=UTF-8"}}
                  resp)))) 
  (testing "string InputStream"
    (let [body           "hello"
          body-as-stream (ByteArrayInputStream. (.getBytes body StandardCharsets/UTF_8))
          resp           (run-response-type-test body-as-stream)]
      (is (match? {:status  200
                   :body    body
                   :headers {"content-type" "text-plain; charset=UTF-8"}}
                  resp))))
  (testing "File"
    (let [body           "hello"
          file           (doto (java.io.File/createTempFile "server-test-" ".txt")
                           (spit body)) 
          resp           (run-response-type-test file)]
      (is (match? {:status  200
                   :body    body
                   :headers {"content-type" "text-plain; charset=UTF-8"}}
                  resp)))))

