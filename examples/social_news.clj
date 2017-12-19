;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(require
  '[clojure.edn :as edn]
  '[clojure.java.io :as io]
  '[datomic.client.api :as d])

(def cfg {:server-type :peer-server
          :access-key "myaccesskey"
          :secret "mysecret"
          :endpoint "localhost:8998"})

(def client (d/client cfg))
(def conn (d/connect client {:db-name "client-example-social-news"}))

(->>
  (-> (io/file "examples/social-news.edn") slurp edn/read-string)
  (map #(d/transact conn {:tx-data %}))
  (into []))

(def db (d/db conn))

(def all-stories
  (d/q '[:find ?e
         :where [?e :story/url]]
       db))

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
  (d/transact conn {:tx-data new-user}))

(def change-user-name-result
  "Demonstrates upsert."
  (d/transact
    conn
    {:tx-data [{:user/email "john@example.com" ;; this finds the existing entity
                :user/firstName "Johnathan"}]}))

(->> {:query '[:find ?story
               :in $ ?e
               :where [?e :user/upVotes ?story]
               [?story :story/url "http://www.paulgraham.com/avg.html"]]
      :args [(d/db conn)
             [:user/email "john@example.com"]]}
     d/q
     ffirst
     (def johns-upvote-for-pg))

(->> {:tx-data [[:db/retract [:user/email "john@example.com"]
                 :user/upVotes johns-upvote-for-pg]]}
     (d/transact conn)
     :db-after
     (def db))

(d/pull db '[*] [:user/email "john@example.com"])

(def data-that-retracts-johns-upvotes
  (d/q '[:find ?op ?e ?a ?v
         :in $ ?op ?e ?a
         :where [?e ?a ?v]]
       db :db/retract [:user/email "john@example.com"] :user/upVotes))

(->> (d/transact conn {:tx-data data-that-retracts-johns-upvotes})
     :db-after
     (def db))

;; all upvotes gone
(d/pull db '[*] [:user/email "john@example.com"])

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

(->> (d/transact conn {:tx-data (vec ten-new-users)})
     :db-after
     (def db))

;; how many users are there? 
(d/q '[:find (count ?e)
       :where [?e :user/email ?v]]
     db)

;; how many users have upvoted something?
(d/q '[:find (count ?e)
       :where [?e :user/email]
       [?e :user/upVotes]]
     db)

;; Datomic does not need a left join to keep entities missing
;; some attribute. Just leave that attribute out of the :where,
;; and then pull it during the :find.
(-> (d/q '[:find (pull ?e [:user/email {:user/upVotes [:story/url]}])
           :where [?e :user/email]]
         db)
    pp/pprint)