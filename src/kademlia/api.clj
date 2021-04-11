(ns kademlia.api
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as str]))


(def localhost "http://127.0.0.1:8080")

(def default-timeout "milliseconds" 1000)

(def default-swap-retry-delay
  "How long to wait (approximately) between retrying swap! operations which
  failed. In milliseconds."
  100)

(defn connect
  "Creates a new etcd client for the given server URI. Example:
  (def etcd (connect \"http://127.0.0.1:4001\"))
  Options:
  :timeout            How long, in milliseconds, to wait for requests.
  :swap-retry-delay   Roughly how long to wait between CAS retries in swap!"
  ([server-uri]
   (connect server-uri {}))
  ([server-uri opts]
   (merge {:timeout           default-timeout
           :swap-retry-delay  default-swap-retry-delay
           :endpoint          server-uri}
          opts)))

(defn slash [client]
  (->
    (client/get (str (:endpoint client) "/"))
    ;; (select-keys [:body :request-time])
    (:body)
    ;; (json/read-str)
    ))

(defn api [client]
  (->
    (client/get (str (:endpoint client) "/api/"))
    ;; (select-keys [:body :request-time])
    (:body)
    (json/read-str)
    ))

(defn read-key [client key]
  (let [key (->
              (client/post (str (:endpoint client) "/api/readKey") {:form-params {:readkey (str key)}})
              ;; (select-keys [:body :request-time])
              (:body)
              (json/read-str)
              vals
              first)]
    (if (re-matches #"key not found.*" key)
      nil key)))

(defn read-all [client]
  (->
    (client/get (str (:endpoint client) "/api/readAll"))
    ;; (select-keys [:body :request-time])
    (:body)
    (json/read-str)))

(defn search [client key]
  (let [key (->
              (client/post (str (:endpoint client) "/api/searchValueByKey") {:form-params {:searchkey (str key)}})
              ;; (select-keys [:body :request-time])
              (:body)
              (json/read-str)
              vals
              first)]
    (if (re-matches #"value not found.*" key)
      nil key)))

(defn insert [client key value]
  (let [key (->
              (client/post (str (:endpoint client) "/api/insert") {:form-params {:insertkey (str key) :insertval (str value)}})
              ;; (select-keys [:body :request-time])
              (:body)
              (json/read-str)
              vals
              first
              (str/split #" ")
              first)]
    key))

(defn read-neighbours [client]
  (->
    (client/get (str (:endpoint client) "/api/readNeighbors"))
    ;; (select-keys [:body :request-time])
    (:body)
    (json/read-str)))


(comment
  (def k-client (connect localhost))

  (client/get (:endpoint k-client))
  
  (slash k-client)

  (api k-client)
  
  (read-key k-client "Anime")

  (read-key k-client "5")

  (read-all k-client)

  (search k-client "5")
  (search k-client "Anime")

  (insert k-client "5" "3")

  (insert k-client "test" "test")

  (read-neighbours k-client)
  
  )

