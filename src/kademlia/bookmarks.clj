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
            [verschlimmbesserung.core :as v]))

;; (def dir "/opt/etcd")

(def dir "/opt/kademlia")




;; (def binary "etcd")

(def binary "kademlia")


(def logfile (str dir "/kademlia.log"))
(def pidfile (str dir "/kademlia.pid"))

;; (defn parse-long
;;   "Parses a string to a Long. Passes through `nil`."
;;   [s]
;;   (when s (Long/parseLong s)))

;; (defn node-url
;;   "An HTTP url for connecting to a node on a particular port."
;;   [node port]
;;   (str "http://" node ":" port))

;; (defn peer-url
;;   "The HTTP url for other peers to talk to a node."
;;   [node]
;;   (node-url node 2380))

;; (defn client-url
;;   "The HTTP url clients use to talk to a node."
;;   [node]
;;   (node-url node 2379))

;; (defn initial-cluster
;;   "Constructs an initial cluster string for a test, like
;;   \"foo=foo:2380,bar=bar:2380,...\""
;;   [test]
;;   (->> (:nodes test)
;;        (map (fn [node]
;;               (str node "=" (peer-url node))))
;;        (str/join ",")))

;; (defn initialise-cluster
;;   "Constructs an initial cluster string for a test, like
;;   \"foo=foo:2380,bar=bar:2380,...\""
;;   [test]
;;   (->> test


;;        ))

(defn db
  "Etcd DB for a particular version."
  []
  (reify db/DB
    (setup! [_ test node]
      (info node "installing kademlia")
      (c/su
        ;; (let [url "https://www.mediafire.com/file/3rkph5wfthufjxf/kademlia.tar.gz/file"
        ;;       ;; (str "https://storage.googleapis.com/etcd/" version
        ;;       ;;      "/etcd-" version "-linux-amd64.tar.gz")
        ;;       ])
        
        ;; (try (c/exec :dpkg-query :-l :libc6)
        ;;      (catch RuntimeException _
        ;;        (info "Installing GLIBC")
        ;;        ))

        
        ;; (info "Installing GLIBC")
        ;; (c/exec :apt-get :install :-y :libc6)

        ;; (try (c/exec :dpkg-query :-l :golang-go)
        ;;      (catch RuntimeException _
        ;;        (info "Installing golang-go")
        ;;        (c/exec :apt-get :install :-y :golang-go)))
        
        ;; (try (c/exec :dpkg-query :-l :git)
        ;;      (catch RuntimeException _
        ;;        (info "Installing git")
        ;;        (c/exec :apt-get :install :-y :git)))
        
        ;; (c/exec :env "GOPATH=~/gocode"
        ;;         :go :get :-u "github.com/zackteo/KademliaBookmarks")


        ;; Create binary with command node ...

        ;; apt-get install -y golang-go
        ;; go bulid
        ;; mv main ../kademlia.bookmarks/resources/kademlia
        
        (c/exec :mkdir :-p dir)
        (c/upload "resources/kademlia" (str dir "/" binary))
        (c/exec :chmod :+x (str dir "/" binary))
        
        (cu/start-daemon!
          {:logfile logfile
           :pidfile pidfile
           :chdir   dir}
          binary
          (net/ip (name node))
          (net/ip "n1")
          )

        (Thread/sleep 90000)))

    (teardown! [_ test node]
      (info node "tearing down kademlia")
      (cu/stop-daemon! binary pidfile)
      (c/su (c/exec :rm :-rf dir))
      )
    
    db/LogFiles
    (log-files [_ test node]
      [logfile])))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

;; (defrecord Client [conn]
;;   client/Client
;;   (open! [this test node]
;;     (assoc this :conn (v/connect (client-url node)
;;                                  {:timeout 5000})))

;;   (setup! [this test])

;;   (invoke! [this test op]
;;     (case (:f op)
;;       :read (assoc op :type :ok, :value (parse-long (v/get conn "foo")))
;;       :write (do (v/reset! conn "foo" (:value op))
;;                  (assoc op :type :ok))
;;       :cas (try+
;;              (let [[old new] (:value op)]
;;                (assoc op :type (if (v/cas! conn "foo" old new)
;;                                  :ok
;;                                  :fail)))
;;              (catch [:errorCode 100] ex
;;                (assoc op :type :fail, :error :not-found)))))

;;   (teardown! [this test])

;;   (close! [_ test]
;;     ;; If our connection were stateful, we'd close it here. Verschlimmmbesserung
;;     ;; doesn't actually hold connections, so there's nothing to close.
;;     ))

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
          ;; :client          (Client. nil)
          ;; :nemesis         (nemesis/partition-random-halves)
          ;; :checker         (checker/compose
          ;;                    {:perf   (checker/perf)
          ;;                     :linear (checker/linearizable
          ;;                               {:model     (model/cas-register)
          ;;                                :algorithm :linear})
          ;;                     :timeline (timeline/html)})
          ;; :generator (->> (gen/mix [r w cas])
          ;;                 (gen/stagger 1/50)
          ;;                 (gen/nemesis
          ;;                   (cycle [(gen/sleep 5)
          ;;                           {:type :info, :f :start}
          ;;                           (gen/sleep 5)
          ;;                           {:type :info, :f :stop}]))
          ;;                 (gen/time-limit (:time-limit opts)))
          }))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn kademlia-test})
                   (cli/serve-cmd))
            args))

;; Just a tester

;; (defn -main
;;   "Handles command line arguments. Can either run a test, or a web server for
;;   browsing results."
;;   [& args]
;;   (api/dodo args))
