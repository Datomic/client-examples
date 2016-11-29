;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require '[datomic.client :as client]
         '[clojure.core.async :refer [<!!] :as a]
         '[clojure.pprint :as pp])

(def conn (<!! (client/connect {:db-name "client-example-likes"})))

;; schema
(<!!
  (client/transact conn
    {:tx-data
     [{:db/ident :likes
       :db/valueType :db.type/ref
       :db/cardinality :db.cardinality/many}
      {:db/ident :name
       :db/valueType :db.type/string
       :db/unique :db.unique/identity
       :db/cardinality :db.cardinality/many}]}))


;; data
(<!!
  (client/transact conn
    {:tx-data
     [{:db/id "broccoli"
       :name "broccoli"}
      {:db/id "pizza"
       :name "pizza"}
      {:name "jane"
       :likes ["broccoli" "pizza"]}]}))

(<!!
  (client/transact conn
    {:tx-data
     [[:db/retract [:name "jane"] :likes [:name "pizza"]]]}))

(def history (-> conn client/db client/history))

(<!!
 (client/q conn {:query '[:find ?name ?t ?op
                          :in $ ?e
                          :where [?e :likes ?v ?t ?op]
                                 [?v :name ?name]]
                 :args [history [:name "jane"]]}))

(def db (-> conn client/db))

(<!!
 (client/pull db {:selector [{:likes [:name]}]
                  :eid [:name "jane"]}))


(<!!
 (client/pull db {:selector [{:_likes [:name]}]
                  :eid [:name "broccoli"]}))


(<!! (client/pull db '{:eid [:name "jane"]
                       :selector [*]}))






