;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require
 '[clojure.core.async :refer (<!!)]
 '[clojure.edn :as edn]
 '[clojure.java.io :as io]
 '[datomic.client :as client])

(def conn (<!! (client/connect {:db-name "client-example-social-news"})))

(->>
 (-> (io/file "examples/social-news.edn") slurp edn/read-string)
 (map #(<!! (client/transact conn {:tx-data %})))
 (into []))

(def db (client/db conn))

(def all-stories
  (->> (client/q conn {:query '[:find ?e
                                :where [?e :story/url]]
                       :args [db]})
       <!!))

(def new-user
  "Transaction data for a new user"
  (conj (mapv
         (fn [[story]] [:db/add "john" :user/upVotes story])
         all-stories)
        {:db/id "john"
         :user/email "john@example.com"
         :user/firstName "John"
         :user/lastName "Doe"}))

(def new-user-result
  "In a single transaction, create new user and upvote all stories"
  (<!! (client/transact conn {:tx-data new-user})))

(def change-user-name-result
  "Demonstrates upsert."
  (<!! (client/transact
        conn
        {:tx-data [{:user/email "john@example.com" ;; this finds the existing entity
                    :user/firstName "Johnathan"}]})))

(->> {:query '[:find ?story
               :in $ ?e
               :where [?e :user/upVotes ?story]
               [?story :story/url "http://www.paulgraham.com/avg.html"]]
      :args [(client/db conn)
             [:user/email "john@example.com"]]}
     (client/q conn)
     <!!
     ffirst
     (def johns-upvote-for-pg))

(->> {:tx-data [[:db/retract [:user/email "john@example.com"]
                 :user/upVotes johns-upvote-for-pg]]}
     (client/transact conn)
     <!!
     :db-after
     (def db))

(<!! (client/pull
      db
      {:eid [:user/email "john@example.com"]
       :selector '[*]}))


(def data-that-retracts-johns-upvotes
  (->> (client/q conn
                 {:query '[:find ?op ?e ?a ?v
                           :in $ ?op ?e ?a
                           :where [?e ?a ?v]]
                  :args [db
                         :db/retract
                         [:user/email "john@example.com"]
                         :user/upVotes]})
       <!!))

(->> (client/transact conn {:tx-data data-that-retracts-johns-upvotes})
     <!!
     :db-after
     (def db))

;; all upvotes gone
(<!! (client/pull db {:eid [:user/email "john@example.com"]
                      :selector '[*]}))

(defn sample-users-with-upvotes
  "Make transaction data for example users, possibly with upvotes"
  [stories n]
  (map
   (fn [n]
     {:user/email (str "sample-" n "@example.com")
      :user/upVotes (take (rand-int (inc (count stories))) (shuffle stories))})
   (range n)))

(def ten-new-users
  (sample-users-with-upvotes (map first all-stories) 10))

(->> (client/transact conn {:tx-data (vec ten-new-users)})
     <!!
     :db-after
     (def db))

;; how many users are there? 
(<!! (client/q conn {:query '[:find (count ?e)
                              :where [?e :user/email ?v]]
                     :args [db]}))

;; how many users have upvoted something?
(<!! (client/q conn {:query '[:find (count ?e)
                              :where [?e :user/email]
                                     [?e :user/upVotes]]
                     :args [db]}))

;; Datomic does not need a left join to keep entities missing
;; some attribute. Just leave that attribute out of the :where,
;; and then pull it during the :find.
(-> (client/q conn {:query '[:find (pull ?e [:user/email {:user/upVotes [:story/url]}])
                             :where [?e :user/email]]
                    :args [db]})
    <!!
    pp/pprint)
