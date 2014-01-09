; Copyright © 2014, Dylan Bijnagte All Rights Reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file LICENSE.txt in this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns alchemist
  (:require [datomic.api :as d :refer (q)]
            [clojure.tools.logging :as log]
            [alchemist.scanner :as scanner]
            [alchemist.util :refer (version-comparator
                                     higher-version?
                                     pprn-str)]))

(def test-uri "datomic:mem://testdb")

(def alchemist-schema [{:db/id #db/id[:db.part/db]
                        :db/ident :alchemist/version
                        :db/valueType :db.type/string
                        :db/cardinality :db.cardinality/one
                        :db/doc "the version of the migration"
                        :db.install/_attribute :db.part/db}
                       
                       {:db/id #db/id[:db.part/db]
                        :db/ident :alchemist/description
                        :db/valueType :db.type/string
                        :db/cardinality :db.cardinality/one
                        :db/doc "the description of the migration"
                        :db.install/_attribute :db.part/db}
                       
                       {:db/id #db/id[:db.part/db]
                        :db/ident :alchemist/hash
                        :db/valueType :db.type/long
                        :db/cardinality :db.cardinality/one
                        :db/doc "a hash of the migration contents"
                        :db.install/_attribute :db.part/db}
                       ])

(defn install-alchemist-schema
  [conn]
  (log/debugf "installing alchemist schema: %s" (ppr-str alchemist-schema))
  @(d/transact conn alchemist-schema))

(def default-config {:create true
                      :scan true
                      :verify true
                      :update true
                      :parent-directories ["transmutations"]
                      :cp-excludes [#".*/?jre/\S*.jar"
                                    #".*/?clojure\S*.jar"
                                    #".*/?datomic\S*.jar"
                                    #".*/?guava\S*.jar"
                                    #".*/?fressian\S*.jar"
                                    #".*/?h2\S*.jar"
                                    #".*/?hornetq-core\S*.jar"
                                    #".*/?netty\S*.jar"
                                    #".*/?slf4j\S*.jar"
                                    #".*/?log4j\S*.jar"
                                    #".*/?bin"]})

(defn connect
  [uri create]
  (if (and create
           (d/create-database uri))
    (let [conn (d/connect uri)]
      (log/debugf "creating db at uri: %s" uri)
      (install-alchemist-schema conn)
      conn)
    (d/connect uri)))

(defn transmutation-history
  [db]
  (->> db
    (q '[:find ?e :where [?e :alchemist/version]])
    (map #(d/entity db (first %)))))

(defn relativize-temp-id
  [base-index element]
  (assoc element :db/id (- (.idx (:db/id element)) base-index)))

(defn relativize-temp-ids
  [transaction]
  (let [[{first-id :db/id}] transaction
        index (.idx first-id)]
    (map #(relativize-temp-id index %) transaction)))

(defn hash-transaction
  [tx]
  (-> tx relativize-temp-ids hash))

(defn annotate-transaction
  [version description hash transaction]
  (let [tx {:db/id #db/id[:db.part/tx -1]}]
    (into transaction
          [(assoc tx :alchemist/version version)
           (assoc tx :alchemist/description description)
           (assoc tx :alchemist/hash hash)])))

(defn run-transmutations
  [conn transmutations]
  (log/infof "running %d transmutations" (count transmutations))
  
  (for [t transmutations
        :let [{version :alchemist/version} t
              {description :alchemist/description} t
              transaction ((:transaction-fn t))
              hash (hash-transaction transaction)]]
    (do
      (log/infof "committing: %s %s" version description)
      (log/debugf "raw transaction: %s" (pprn-str transaction))
      @(d/transact conn (annotate-transaction version description hash transaction)))))

(defn sort-by-version
  [transmutations]
  (sort
    (fn [a b] 
      (version-comparator (:alchemist/version a) (:alchemist/version b)))
    transmutations))

(defn current-version
  [entities]
  (-> 
    entities
    sort-by-version
    last
    :alchemist/version))

(defn split-at-version
  [version transmutations]
  (split-with #(higher-version? (:alchemist/version %) version) transmutations))

(defn same-transmutations?
  [run history]
  (log/infof "verifying %d transmutations" (count run))
  (if-not (= (count run) (count history))
    false ;;fail fast
    (loop [run (sort-by-version run) history (sort-by-version history)]
      (if (and (empty? run)
               (empty? history))
        true
        (let [r (first run) h (first history)]
          (log/debugf "comparing run: %s transmutation history: %s"
                      (pprn-str r) (pprn-str h))
          (if (and r
                   (= (:alchemist/version r)
                      (:alchemist/version h))
                   (= (hash-transaction ((:transaction-fn r)))
                      (:alchemist/hash h)))
            (do
              (log/infof "verified version %s" (:alchemist/version r))
              (recur (rest run) (rest history)))
            (do
              (log/errorf
                "verification failed! transmutation run: %s transmutation history: %s"
                (pprn-str r) (pprn-str h))
              false)))))))

(defn handle-run
  [conn verify transmutations]
  (let [history (transmutation-history (d/db conn))]
    (if (empty? history)
      transmutations
      (let [version (current-version history)
            [new run] (split-at-version version transmutations)]
        (when verify
          (when-not (same-transmutations? run history)
            (throw (Exception. "transmutation verification failed!"))))
        new))))

  (defn transmute
    ([uri] (transmute uri default-config))
    ([uri config](transmute uri config nil))      
    ([uri config transmutations]
      (let [{:keys [create scan verify update]} config
            conn (connect uri create)
            transmutations (->>
                             transmutations
                             (into (scanner/find-transmutations config))
                             sort-by-version)]
        (log/debugf "transmutations: %s" (ppr-str transmutations))
        (let [transmutations (handle-run conn verify transmutations)]
          (when update (run-transmutations conn transmutations))))))