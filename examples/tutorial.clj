(require '[datomic.client :as client]
         '[clojure.core.async :refer (<!!)])

(def conn (<!! (client/connect {:db-name "client-example-tutorial"})))

(def colors [:red :green :blue :yellow])
(def sizes [:small :medium :large :xlarge])
(def types [:shirt :pants :dress :hat])

(defn make-idents
  [x]
  (mapv #(hash-map :db/ident %) x))

(<!! (client/transact conn {:tx-data (make-idents colors)}))
(<!! (client/transact conn {:tx-data (make-idents sizes)}))
(<!! (client/transact conn {:tx-data (make-idents types)}))

(def schema-1
  [{:db/ident :inv/sku
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}
   {:db/ident :inv/color
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :inv/size
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :inv/type
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}])

(<!! (client/transact conn {:tx-data schema-1}))

(def sample-data
  (->> (for [color colors size sizes type types]
         {:inv/color color
          :inv/size size
          :inv/type type})
       (map-indexed
        (fn [idx map]
          (assoc map :inv/sku (str "SKU-" idx))))))
sample-data

(<!! (client/transact conn {:tx-data sample-data}))

(def db (client/db conn))

[:inv/sku "SKU-42"]

;; pull
(<!! (client/pull
      db
      {:eid [:inv/sku "SKU-42"]
       :selector [{:inv/color [:db/ident]}
                  {:inv/size [:db/ident]}
                  {:inv/type [:db/ident]}]}))

;; same color as SKU-42
(<!! (client/q
      conn
      {:query '[:find ?e ?sku
                :where
                [?e :inv/sku "SKU-42"]
                [?e :inv/color ?color]
                [?e2 :inv/color ?color]
                [?e2 :inv/sku ?sku]]
       :args [db]}))

(def order-schema
  [{:db/ident :order/items
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true}
   {:db/ident :item/id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :item/count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}])

(<!! (client/transact conn {:tx-data order-schema}))

(def add-order
  {:order/items
   [{:item/id [:inv/sku "SKU-25"]
     :item/count 10}
    {:item/id [:inv/sku "SKU-26"]
     :item/count 20}]})

(<!! (client/transact conn {:tx-data [add-order]}))

(def db (client/db conn))
;; items that appear in the same order as SKU-25
(<!! (client/q
      conn
      {:query '[:find ?sku
                :in $ ?inv
                :where
                [?item :item/id ?inv]
                [?order :order/items ?item]
                [?order :order/items ?other-item]
                [?other-item :item/id ?other-inv]
                [?other-inv :inv/sku ?sku]]
       :args [db [:inv/sku "SKU-25"]]}))

(def rules
  '[[(ordered-together ?inv ?other-inv)
     [?item :item/id ?inv]
     [?order :order/items ?item]
     [?order :order/items ?other-item]
     [?other-item :item/id ?other-inv]]])

(<!! (client/q
      conn
      {:query '[:find ?sku
                :in $ % ?inv
                :where
                (ordered-together ?inv ?other-inv)
                [?other-inv :inv/sku ?sku]]
       :args [db rules [:inv/sku "SKU-25"]]}))

;; time
(def inventory-counts
  [{:db/ident :inv/count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}])

(<!! (client/transact conn {:tx-data inventory-counts}))

(def inventory-update
  [[:db/add [:inv/sku "SKU-21"] :inv/count 7]
   [:db/add [:inv/sku "SKU-22"] :inv/count 7]
   [:db/add [:inv/sku "SKU-42"] :inv/count 100]])

(<!! (client/transact conn {:tx-data inventory-update}))

;; never had any 22s
(<!! (client/transact
      conn
      {:tx-data [[:db/retract [:inv/sku "SKU-22"] :inv/count 7]
                 [:db/add "datomic.tx" :db/doc "remove incorrect assertion"]]}))

;; correct count for 42s
(<!! (client/transact
      conn
      {:tx-data [[:db/add [:inv/sku "SKU-42"] :inv/count 1000]
                 [:db/add "datomic.tx" :db/doc "correct data entry error"]]}))


(def db (client/db conn))
(<!! (client/q
      conn
      {:query '[:find ?sku ?count
                :where
                [?inv :inv/sku ?sku]
                [?inv :inv/count ?count]]
       :args [db]}))

;; transactions
(def txid (->> (<!! (client/q
                     conn
                     {:query '[:find (max 3 ?tx)
                               :where
                               [?tx :db/txInstant]]
                      :args [db]}))
               first first last))

;; as-of query
(def db-before
  (client/as-of db txid))

(<!! (client/q
      conn
      {:query '[:find ?sku ?count
                :where
                [?inv :inv/sku ?sku]
                [?inv :inv/count ?count]]
       :args [db-before]}))

;; history query
(require '[clojure.pprint :as pp])
(def db-hist (client/history db))
(->> (<!! (client/q
           conn
           {:query '[:find ?tx ?sku ?val ?op
                     :where
                     [?inv :inv/count ?val ?tx ?op]
                     [?inv :inv/sku ?sku]]
            :args [db-hist]}))
     (sort-by first)
     (pp/pprint))

