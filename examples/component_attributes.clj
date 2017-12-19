;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pp]
         '[datomic.client.api :as d])

(def cfg {:server-type :peer-server
          :access-key "myaccesskey"
          :secret "mysecret"
          :endpoint "localhost:8998"})

(def client (d/client cfg))
(def conn (d/connect client {:db-name "client-example-component-attributes"}))
(def schema (-> (io/file "examples/social-news.edn") slurp edn/read-string))
(mapv #(d/transact conn {:tx-data %}) schema)

;; create a story and some comments
(let [{:keys [tempids db-after] :as res}
      (d/transact conn
                  {:tx-data
                   [{:db/id "story"
                     :story/title "Getting Started"
                     :story/url "http://docs.datomic.com/getting-started.html"}
                    {:db/id "comment-1"
                     :comment/body "It woud be great to learn about component attributes."
                     :_comments "story"}
                    {:db/id "comment-2"
                     :comment/body "I agree."
                     :_comments "comment-1"}]})]
  (def story (get tempids "story"))
  (def comment-1 (get tempids "comment-1"))
  (def comment-2 (get tempids "comment-2"))
  (def db db-after))

(pp/pprint (d/pull db '[*] story))

;; retract the story
(def retracted-es
  (->> (d/transact conn {:tx-data [[:db/retractEntity story]]})
       :tx-data
       (remove :added)
       (map :e)
       (into #{})))

;; retraction recursively retracts component comments
(= retracted-es #{story comment-1 comment-2})
