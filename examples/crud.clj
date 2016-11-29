;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require '[datomic.client :as client]
         '[clojure.core.async :refer [<!!]])

(def conn (<!! (client/connect {:db-name "client-example-crud"})))

;; attribute schema for :crud/name
(<!!
  (client/transact conn
    {:tx-data
     [{:db/id "schema"
       :db/ident :crud/name
       :db/valueType :db.type/string
       :db/unique :db.unique/identity
       :db/cardinality :db.cardinality/one}]}))

;; create, awaiting point-in-time-value
(def db-after-create
  (-> (client/transact conn
        {:tx-data [[:db/add "entity" :crud/name "Hello world"]]})
    <!! :db-after))

;; read
(<!! (client/pull db-after-create
       {:selector '[*]
        :eid [:crud/name "Hello world"]}))

;; update
(-> (client/transact conn
        {:tx-data
         [[:db/add [:crud/name "Hello world"]
           :db/doc "An entity with only demonstration value"]]})
    <!!
    :db-after
    (client/pull
      {:selector '[*]
       :eid [:crud/name "Hello world"]})
    <!!)

;; "delete" adds new information, does not erase old
(def db-after-delete
  (-> (client/transact conn
        {:tx-data [[:db/retractEntity [:crud/name "Hello world"]]]})
    <!! :db-after))

;; no present value for deleted entity
(<!! (client/pull db-after-delete
       {:selector '[*]
        :eid [:crud/name "Hello world"]}))

;; but everything ever said is still there
(def history (client/history db-after-delete))

(require '[clojure.pprint :as pp])

(->> (client/q conn
       {:query '[:find ?e ?a ?v ?tx ?op
                 :in $
                 :where [?e :crud/name "Hello world"]
                 [?e ?a ?v ?tx ?op]]
        :args [history]})
  <!!
  (map #(zipmap [:e :a :v :tx :op] %))
  (sort-by :tx)
  (pp/print-table [:e :a :v :tx :op]))
