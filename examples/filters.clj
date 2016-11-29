;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require
 '[clojure.pprint :as pp]
 '[datomic.client :as client]
 '[clojure.core.async :refer (<!!)])

(def conn (<!! (client/connect {:db-name "client-example-filters"})))

(def txes
  [[{:db/ident :item/id
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db/unique :db.unique/identity}
    {:db/ident :item/description
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db/fulltext true}
    {:db/ident :item/count
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db/index true}
    {:db/ident :tx/error
     :db/valueType :db.type/boolean
     :db/index true
     :db/cardinality :db.cardinality/one}
    {:db/id "datomic.tx"
     :db/txInstant #inst "2012"}]
   [{:item/id "DLC-042"
     :item/description "Dilitihium Crystals"
     :item/count 100}
    {:db/id "datomic.tx"
     :db/txInstant #inst "2013-01"}]
   [{:db/id [:item/id "DLC-042"]
     :item/count 250}
    {:db/id "datomic.tx"
     :db/txInstant #inst "2013-02"}]
   [{:db/id [:item/id "DLC-042"]
     :item/count 50}
    {:db/id "datomic.tx"
     :db/txInstant #inst "2014-02-28"}]
   [{:db/id [:item/id "DLC-042"]
     :item/count 9999}
    {:db/id "datomic.tx"
     :db/txInstant #inst "2014-04-01"
     :tx/error true}]
   [{:db/id [:item/id "DLC-042"]
     :item/count 100}
    {:db/id "datomic.tx"
     :db/txInstant #inst "2014-05-15"}]])

(def results (map
              #(<!! (client/transact conn {:tx-data %}))
              txes))

(def db (client/db conn))
(def as-of-eoy-2013 (client/as-of db #inst "2014-01-01"))
(def since-2014 (client/since db #inst "2014-01-01"))
(def history (client/history db))

(defn trunc
  "Return a string rep of x, shortened to n chars or less"
  [x n]
  (let [s (str x)]
    (if (<= (count s) n)
      s
      (str (subs s 0 (- n 3)) "..."))))

(def tx-part-e-a-added
  "Sort datoms by tx, then e, then a, then added"
  (reify
   java.util.Comparator
   (compare
    [_ x y]
    (cond
     (< (:tx x) (:tx y)) -1
     (> (:tx x) (:tx y)) 1
     (< (:e x) (:e y)) -1
     (> (:e x) (:e y)) 1
     (< (:a x) (:a y)) -1
     (> (:a x) (:a y)) 1
     (false? (:added x)) -1
     :default 1))))

(defn datom-table
  "Print a collection of datoms in an org-mode compatible table."
  [db datoms]
  (let [attr (memoize (fn [eid]
                        (:db/ident (<!! (client/pull db {:eid eid
                                                         :selector [:db/ident]})))))]
    (->> datoms
         (map
          (fn [{:keys [e a v t added]}]
            {"e" (format "0x%016x" e)
             "a" (attr a)
             "v" (trunc v 24)
             "t" (format "0x%x" t)
             "added" added}))
         (pp/print-table ["e" "a" "v" "t" "added"]))))

;; print db as a table
(->> (client/tx-range conn {:start #inst "2012"})
     <!!
     (sequence
      (comp
       (halt-when client/error?)
       (mapcat :data)))
     (datom-table db))

;; full history of dilithium crystal assertions
(->> (client/q conn {:query '[:find ?aname ?v ?inst
                              :in $ ?e
                              :where [?e ?a ?v ?tx true]
                              [?tx :db/txInstant ?inst]
                              [?a :db/ident ?aname]]
                     :args [history [:item/id "DLC-042"]]})
     <!!
     (sort-by #(nth % 2))
     pp/pprint)

;; full history of dilithium crystal counts
(->> (client/q conn {:query '[:find ?inst ?count
                              :in $ ?id
                              :where [?id :item/count ?count ?tx true]
                              [?tx :db/txInstant ?inst]]
                     :args [history [:item/id "DLC-042"]]})
     <!!
     (sort-by first)
     pp/pprint)

;; corrected history of dilithium crystal counts
(->> (client/q conn {:query '[:find ?inst ?count
                              :in $ ?id
                              :where
                              [?id :item/count ?count ?tx true]
                              [?tx :db/txInstant ?inst]
                              (not [?tx :tx/error])]
                     :args [history [:item/id "DLC-042"]]})
     <!!
     (sort-by first)
     pp/pprint)


