(defproject kademlia.bookmarks "0.1.0-SNAPSHOT"
  :description "A Jepsen test for kademlia bookmarks"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [jepsen "0.2.3"]
                 [clj-http "3.12.1"]
                 [verschlimmbesserung "0.1.3"]]
  :main kademlia.bookmarks
  :repl-options {:init-ns kademlia.bookmarks})
