(ns kademlia.bookmarks
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [checker :as checker]
             [cli :as cli]
             [client :as client]
             [control :as c]
             [db :as db]
             [generator :as gen]
             [nemesis :as nemesis]
             [tests :as tests]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.control.util :as cu]
            [jepsen.control.net :as net]
            [jepsen.os.debian :as debian]
            [knossos.model :as model]
            [slingshot.slingshot :refer [try+]]
            [verschlimmbesserung.core :as v]
            [kademlia.api :as api]))

(def dir "/opt/kademlia")
(def binary "kademlia")

(def logfile (str dir "/kademlia.log"))
(def pidfile (str dir "/kademlia.pid"))



(defn node-url
  "An HTTP url for connecting to a node on a particular port."
  [node port]
  (str "http://" node ":" port))

(defn client-url
  "The HTTP url clients use to talk to a node."
  [node]
  (node-url (net/ip (name node)) 8080)) 

(defn parse-long
  "Parses a string to a Long. Passes through `nil`."
  [s]
  (when s (Long/parseLong s)))

;; Create binary with command node ...

;; apt-get install -y golang-go
;; go bulid
;; mv main ../kademlia.bookmarks/resources/kademlia
;; cp -r templates ../kademlia.bookmarks/resources/templates

(defn db
  "Etcd DB for a particular version."
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "installing kademlia")
      (c/su
        (c/exec :mkdir :-p dir)
        (c/upload "resources/kademlia" (str dir "/" binary))
        (c/exec :mkdir :-p (str dir "/templates"))
        (c/upload "resources/templates/footer.html" (str dir "/templates/footer.html" ))
        (c/upload "resources/templates/header.html" (str dir "/templates/header.html" ))
        (c/upload "resources/templates/index.html" (str dir "/templates/index.html" ))
        (c/upload "resources/templates/menu.html" (str dir "/templates/menu.html" ))
        (c/exec :chmod :+x (str dir "/" binary))
        
        (cu/start-daemon!
          {:logfile logfile
           :pidfile pidfile
           :chdir   dir}
          binary
          (net/ip (name node))
          (net/ip "n1")
          )

        (Thread/sleep 10000)))

    (teardown! [_ test node]
      (info node "tearing down kademlia")
      (cu/stop-daemon! binary pidfile)
      (c/su (c/exec :rm :-rf dir))
      )
    
    db/LogFiles
    (log-files [_ test node]
      [logfile])))

(defn r      [_ _] {:type :invoke, :f :read, :value nil})
(defn r-all  [_ _] {:type :invoke, :f :read-all, :value nil})
(defn r-nb   [_ _] {:type :invoke, :f :read-neighbours, :value nil})
(defn w      [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn s      [_ _] {:type :invoke, :f :search, :value nil})

(defrecord Client [conn]
  client/Client
  (open! [this test node]
    (assoc this :conn (api/connect (client-url node))))
  (setup! [this test]
    ;; initializes any data structures the test needs--for instance,
    ;; creating tables or setting up fixtures
    )
  (invoke! [this test op]
    (case (:f op)
      :read (assoc op :type :ok, :value (parse-long (api/read-key conn "foo")))
      ;; :read-all (assoc op :type :ok, :value (parse-long (api/read-all conn)))
      ;; :read-neighbours (assoc op :type :ok, :value (parse-long (api/read-neighbours conn)))
      :write (let [ value (:value op)]
               (assoc op :type :ok :value (parse-long (api/insert conn "foo" value))))
      :search (assoc op :type :ok, :value (parse-long (api/search conn "foo")))
      
      ))
  (teardown! [this test])
  (close! [_ test]
    ;; If our connection were stateful, we'd close it here. Verschlimmmbesserung
    ;; doesn't actually hold connections, so there's nothing to close.
    ))

;; (defn generator-old [opts]
;;   (->> (gen/mix [w s])
;;        (gen/stagger 1/50)
;;        (gen/nemesis
;;          (cycle [(gen/sleep 5)
;;                  {:type :info, :f :start}
;;                  (gen/sleep 5)
;;                  {:type :info, :f :stop}]))
;;        (gen/time-limit (:time-limit opts))))

(def inc-seq-r-w
  (sequence
    (comp
      (mapcat identity))
    (map
      (fn [n]
        [{:type :invoke, :f :write, :value n}
         (repeat 150 {:f :read})])
      (range))))

(defn single-key-r-w [opts]
  (->> inc-seq-r-w
       (gen/stagger 1/50)
       (gen/nemesis nil)
       (gen/time-limit 60)))

(defn single-key-r-w-nemesis [opts]
  (->> inc-seq-r-w
       (gen/stagger 1/50)
       (gen/nemesis
         (cycle [(gen/sleep 5)
                 {:type :info, :f :start}
                 (gen/sleep 5)
                 {:type :info, :f :stop}]))
       (gen/time-limit 60)))

(def inc-seq-s-w
  (sequence
    (comp
      (mapcat identity))
    (map
      (fn [n]
        [{:type :invoke, :f :write, :value n}
         (repeat 150 {:f :search})])
      (range))))

(defn single-key-s-w [opts]
  (->> inc-seq-s-w
       (gen/stagger 1/50)
       (gen/nemesis nil)
       (gen/time-limit 60)))

(defn single-key-s-w-nemesis [opts]
  (->> inc-seq-s-w
       (gen/stagger 1/50)
       (gen/nemesis
         (cycle [(gen/sleep 5)
                 {:type :info, :f :start}
                 (gen/sleep 5)
                 {:type :info, :f :stop}]))
       (gen/time-limit 60)))


(defn kademlia-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:pure-generators true
          :name            "kademlia"
          :os              debian/os
          :db              (db)
          :client          (Client. nil)
          :nemesis         (nemesis/partition-random-halves)
          :checker         (checker/compose
                             {:perf   (checker/perf)
                              :rate   (checker/rate-graph)
                              :latency (checker/latency-graph)
                              ;; :linear (checker/linearizable
                              ;;           {:model     (model/register)
                              ;;            :algorithm :linear})
                              :timeline (timeline/html)})
          :generator (single-key-s-w-nemesis opts)
          ;; :generator (->> (gen/mix [r w])
          ;;                 (gen/stagger 1/50)
          ;;                 (gen/nemesis nil)
          ;;                 (gen/time-limit 15))
          }
         ))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn kademlia-test})
                   (cli/serve-cmd))
            args))


;; (defn etcd-test
;;   "Given an options map from the command line runner (e.g. :nodes, :ssh,
;;   :concurrency, ...), constructs a test map."
;;   [opts]
;;   (merge tests/noop-test
;;          {:pure-generators true}
;;          opts))


;; (defn -main
;;   "Handles command line arguments. Can either run a test, or a web server for
;;   browsing results."
;;   [& args]
;;   (cli/run! (merge (cli/single-test-cmd {:test-fn etcd-test})
;;                    (cli/serve-cmd))
;;             args))
