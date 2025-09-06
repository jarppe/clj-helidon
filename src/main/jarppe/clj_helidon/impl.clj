(ns jarppe.clj-helidon.impl
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.reflect :as reflect]
            [ring.core.protocols :as rp]
            [ring.sse]
            [jarppe.clj-helidon.sse :as sse])
  (:import (java.nio.charset StandardCharsets)
           (clojure.lang MapEntry) 
           (io.helidon.http Method
                            Status
                            Header
                            HeaderName
                            HeaderNames
                            HeaderValues
                            ServerRequestHeaders)
           (io.helidon.webserver WebServer)
           (io.helidon.webserver.http Handler
                                      ServerRequest
                                      ServerResponse
                                      HttpRouting
                                      HttpRouting$Builder)))


(set! *warn-on-reflection* true)


;;
;; ============================================================================
;; Helidon Method:
;; ============================================================================
;;


(def ^:private ring-method->helidon-method {:get     Method/GET
                                            :post    Method/POST
                                            :put     Method/PUT
                                            :delete  Method/DELETE
                                            :head    Method/HEAD
                                            :options Method/OPTIONS
                                            :trace   Method/TRACE
                                            :patch   Method/PATCH
                                            :connect Method/CONNECT})


(def ^:private helidon-method->ring-method (set/map-invert ring-method->helidon-method))


;;
;; ============================================================================
;; Helidon Status:
;; ============================================================================
;;


(def ^:private http-status->helidon-status
  (->> (reflect/type-reflect Status)
       :members
       (filter (partial instance? clojure.reflect.Field))
       (filter (comp :static :flags))
       (filter (comp (partial = 'io.helidon.http.Status) :type))
       (map (fn [status-field]
              (-> (.getField Status (name (:name status-field)))
                  (.get nil))))
       (sort-by (fn [^Status status] (.code status)))
       (map (fn [^Status status]
              [(.code status) status]))
       (into {})))


(defn- helidon-status ^Status [status-code]
  (if (instance? Status status-code)
    status-code
    (or (http-status->helidon-status status-code)
        (Status/create status-code))))


;;
;; ============================================================================
;; Helidon HeaderName:
;; ============================================================================
;;


(def ^:private header-name->helidon-header-name
  (->> (reflect/type-reflect HeaderNames)
       :members
       (filter (partial instance? clojure.reflect.Field))
       (filter (comp :static :flags))
       (filter (comp (partial = 'io.helidon.http.HeaderName) :type))
       (map (fn [header-name-field]
              (-> (.getField HeaderNames (name (:name header-name-field)))
                  (.get nil))))
       (map (fn [^HeaderName header-name]
              [(.lowerCase header-name) header-name]))
       (into {})))


(defn- helidon-header-name
  "Maps header names in lower-case kebab string format into `io.helidon.http.HeaderName` instances.
   First tries to use members of Java class `io.helidon.http.Header.HeaderNames`. If requested header is not part 
   of that class, creates a new instance of HeaderName."
  ^HeaderName [header-name]
  (if (instance? HeaderName header-name)
    header-name
    (or (header-name->helidon-header-name header-name)
        (HeaderNames/create header-name))))


;;
;; ============================================================================
;; Helidon Header:
;; ============================================================================
;;


(def ^:private cached-helidon-header-values
  (let [common-header-values         (->> ["content-type"     "application/edn"
                                           "content-type"     "text/html"
                                           "content-encoding" "gzip"]
                                          (partition 2)
                                          (map (fn [[^String header-name ^String header-value]]
                                                 (HeaderValues/create header-name header-value)))
                                          (reduce (fn [acc header]
                                                    (update acc
                                                            (str/lower-case (Header/.name header))
                                                            assoc
                                                            (Header/.value header)
                                                            header))
                                                  {}))
        helidon-cached-header-values (->> (reflect/type-reflect HeaderValues)
                                          :members
                                          (filter (partial instance? clojure.reflect.Field))
                                          (filter (comp :static :flags))
                                          (filter (comp (partial = 'io.helidon.http.Header) :type))
                                          (map (fn [header-value-field]
                                                 (-> (.getField HeaderValues (name (:name header-value-field)))
                                                     (.get nil))))
                                          (reduce (fn [acc ^Header header-value]
                                                    (update acc
                                                            (str/lower-case (.name header-value))
                                                            assoc
                                                            (.value header-value)
                                                            header-value))
                                                  {}))]
    (merge common-header-values
           helidon-cached-header-values)))


(defn helidon-header ^Header [^String header-name ^String header-value]
  (or (-> (get cached-helidon-header-values header-name)
          (get header-value))
      (HeaderValues/create header-name header-value)))


;;
;; ============================================================================
;; Helifon ServerRequestHeaders proxy:
;; ============================================================================
;;


(defn- helidon-header->str ^String [^ServerRequestHeaders headers k not-found]
  (-> headers
      (.first (helidon-header-name k))
      (.orElse not-found)))


(defn- helidon-headers->map-entry-eduction [^ServerRequestHeaders headers]
  (eduction (map (fn [^Header header]
                   (MapEntry/create (-> header .headerName (.lowerCase))
                                    (-> header .value))))
            headers))


(deftype HeadersProxy [^ServerRequestHeaders headers]
  clojure.lang.ILookup
  (valAt [_ k] (helidon-header->str headers k nil))
  (valAt [_ k default-value] (helidon-header->str headers k default-value))

  clojure.lang.IFn
  (invoke [_ k] (helidon-header->str headers k nil))
  (invoke [_ k default-value] (helidon-header->str headers k default-value))

  clojure.lang.Associative
  (containsKey [_ k] (.contains headers (helidon-header-name k)))
  (entryAt [_ k] (when-let [v (helidon-header->str headers k nil)] (MapEntry/create k v)))
  (equiv [this that] (identical? this that))

  clojure.lang.IPersistentCollection
  (count [_] (.size headers))
  (empty [_] {})
  
  clojure.lang.IMeta
  (meta [_] {})

  clojure.lang.Seqable
  (seq [_]
    (->> headers
         (helidon-headers->map-entry-eduction)
         (seq)))

  java.lang.Iterable
  (iterator [_]
    (->> headers
         (helidon-headers->map-entry-eduction)
         (java.lang.Iterable/.iterator)))

  java.lang.Object
  (toString [this] (str "metosin.helidon.impl/HeadersProxy["
                        (->> (seq this)
                             (map (fn [[header-name header-value]]
                                    (str header-name ": " header-value)))
                             (str/join ", "))
                        "]"))
  (equals [this that] (identical? this that)))


(defmethod print-method HeadersProxy [headers-proxy ^java.io.Writer w]
  (.append w (str headers-proxy)))


;;
;; ============================================================================
;; Ring request:
;; ============================================================================
;;


(defn helidon-req->ring-req [^ServerRequest req ^ServerResponse resp]
  (let [prologue (.prologue req)
        local    (.localPeer req)
        query    (.query req)
        path     (.path req)
        content  (.content req)]
    {:server-port      (.port local)
     :server-name      (.host local)
     :remote-addr      (-> (.remotePeer req)
                           (.address)
                           (java.net.InetSocketAddress/.getAddress)
                           (.getHostAddress))
     :uri              (.toString path)
     :query-string     (.rawValue query)
     :protocol         (-> prologue .rawProtocol)
     :scheme           (if (.isSecure req) :https :http)
     :request-method   (-> prologue (.method) helidon-method->ring-method)
     :headers          (->HeadersProxy (.headers req))
     :body             (when (.hasEntity content)
                         (let [content (.content req)]
                           (when-not (.consumed content)
                             (.inputStream content))))
     :helidon/request  req
     :helidon/response resp}))


;;
;; ============================================================================
;; HTTP response body writer:
;; ============================================================================
;;


(defprotocol BodyWriter
  (write-body [this server-response]))


(extend-protocol BodyWriter
  java.io.InputStream
  (write-body [this ^ServerResponse server-response]
    (with-open [out (.outputStream server-response)]
      (io/copy this out)
      (.flush out))
    (.close this)
    nil)

  java.io.Reader
  (write-body [this ^ServerResponse server-response]
    (with-open [out (.outputStream server-response)]
      (io/copy this out)
      (.flush out))
    (.close this)
    nil)

  java.io.File
  (write-body [this ^ServerResponse server-response]
    (with-open [in  (java.io.FileInputStream. this)
                out (.outputStream server-response)]
      (io/copy in out)
      (.flush out))
              nil)

  java.nio.file.Path
  (write-body [this ^ServerResponse server-response]
    (with-open [in  (java.io.FileInputStream. (.toFile this))
                out (.outputStream server-response)]
      (io/copy in out)
      (.flush out)))

  byte/1
  (write-body [this ^ServerResponse server-response]
              (with-open [out (.outputStream server-response)]
                (io/copy this out)
                (.flush out)))
  
  char/1
  (write-body [this ^ServerResponse server-response]
              (with-open [out (.outputStream server-response)]
                (io/copy this out)
                (.flush out)))
  
  String 
  (write-body [this ^ServerResponse server-response]
              (.send server-response (.getBytes this StandardCharsets/UTF_8)))
  
  java.lang.Object
  (write-body [this ^ServerResponse server-response]
              (if (satisfies? rp/StreamableResponseBody this)
                (rp/write-body-to-stream this nil (.outputStream server-response))
                (.send server-response this)))
  
  nil
  (write-body [_ ^ServerResponse server-response]
              (.send server-response)
              true))


;;
;; ============================================================================
;; Helidon ServerResponse:
;; ============================================================================
;;


(defn send-server-resp [ring-resp ^ServerResponse server-resp]
  (.status server-resp (-> ring-resp :status (or 200) (helidon-status)))
  (doseq [ring-header (-> ring-resp :headers)]
    (.header server-resp (helidon-header (key ring-header)
                                         (str (val ring-header)))))
  (write-body (-> ring-resp :body) server-resp))


(defn send-ring-resp [ring-resp ^ServerResponse server-resp]
  (cond
    (ring.sse/sse-response? ring-resp) (sse/handle-sse-resp ring-resp ^ServerResponse server-resp)
    ;; TODO:: WebSocket
    :else (send-server-resp ring-resp server-resp)))


;;
;; ============================================================================
;; Helidon Handler:
;; ============================================================================
;;


(defn- ring-handler->helidon-handler ^Handler [handler]
  (reify Handler
    (handle [_ req resp]
      (-> (helidon-req->ring-req req resp)
          (handler)
          (send-ring-resp resp)))))


;;
;; ============================================================================
;; Helidon routing:
;; ============================================================================
;;


(defn handler->routing ^HttpRouting$Builder [handler]
  (doto (HttpRouting/builder) 
    (.any ^Handler/1 (into-array Handler [(ring-handler->helidon-handler handler)]))))


;;
;; ============================================================================
;; Helidon WebServer:
;; ============================================================================
;;


(defrecord CljHelidonWebServer [^WebServer helidon]
  java.io.Closeable
  (close [_] (.stop helidon)))


(defmethod print-method CljHelidonWebServer [server ^java.io.Writer w]
  (.append w (str "CljHelidonWebServer["
                  "status=\"" (:status server) "\"" 
                  ", "
                  "port=" (:port server)
                  "]")))


(defn create-server [handler {:keys [host port]}]
  (let [server (-> (doto (WebServer/builder)
                     (.host (or host "127.0.0.1"))
                     (.port (cond
                              (integer? port) port
                              (string? port)  (parse-long port)
                              (nil? port)     0))
                     (.routing (handler->routing handler)))
                   (.build)
                   (.start))]
    (->CljHelidonWebServer server)))


(comment
  (defn handler [req]
    {:status  200
     :headers {"content-type" "text/plain"}
     :body    (str "Hullo from " (-> req :headers (get "host")))})

  (require 'ring.sse)

  (defn sse-handler [_]
    {:ring.sse/listener {:on-open (fn [sse]
                                    (with-open [sse sse]
                                      (println "SSE opened")
                                      (doseq [n (range 5 0 -1)]
                                        (ring.sse/send sse {:id   n
                                                            :data (str "countdown " n)})
                                        (Thread/sleep 1000))
                                      (ring.sse/send sse {:id   0
                                                          :data "Liftoff!"})))}})
  
  (def server (create-server sse-handler {:port "8888"}))

  (.close server)
  )