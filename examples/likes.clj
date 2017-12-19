;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require '[datomic.client.api :as d]
         '[clojure.pprint :as pp])

(def cfg {:server-type :peer-server
          :access-key "myaccesskey"
          :secret "mysecret"
          :endpoint "localhost:8998"})

(def client (d/client cfg))
(def conn (d/connect client {:db-name "client-example-likes"}))

;; schema
(d/transact conn
            {:tx-data
             [{:db/ident :likes
               :db/valueType :db.type/ref
               :db/cardinality :db.cardinality/many}
              {:db/ident :name
               :db/valueType :db.type/string
               :db/unique :db.unique/identity
               :db/cardinality :db.cardinality/many}]})

;; data
(d/transact conn
            {:tx-data
             [{:db/id "broccoli"
               :name "broccoli"}
              {:db/id "pizza"
               :name "pizza"}
              {:name "jane"
               :likes ["broccoli" "pizza"]}]})

(d/transact conn {:tx-data
                  [[:db/retract [:name "jane"] :likes [:name "pizza"]]]})

(def history (-> conn d/db d/history))

(d/q '[:find ?name ?t ?op
       :in $ ?e
       :where [?e :likes ?v ?t ?op]
       [?v :name ?name]]
     history [:name "jane"])

(def db (-> conn d/db))

(d/pull db '[{:likes [:name]}] [:name "jane"])

(d/pull db '[{:_likes [:name]}] [:name "broccoli"])

(d/pull db '[*] [:name "jane"])
