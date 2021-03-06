; Copyright © 2014, Dylan Bijnagte All Rights Reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
; which can be found in the file LICENSE.txt in this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.
(ns alchemist.db
    (:require [datomic.api :as d :refer (q)]
              [clojure.tools.logging :as log]
              [alchemist.util :refer (pprn-str)]))

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
  (log/debugf "installing alchemist schema: %s" (pprn-str alchemist-schema))
  @(d/transact conn alchemist-schema))

(defn connect
  [uri create?]
  (when create?
    (when (d/create-database uri)
      (log/debugf "created db at uri: %s" uri)))
  (d/connect uri))

(defn current
  [conn]
  (d/db conn))

(defn find-transmutations
  [db]
  (->> db
    (q '[:find ?e :where [?e :alchemist/version]])
    (map #(d/entity db (first %)))))

(defn transmutation-history
  [conn]
  (find-transmutations (d/db conn)))

(defn annotate-transaction
  [version description hash transaction]
  (let [tx {:db/id #db/id[:db.part/tx -1]}]
    (into transaction
          [(assoc tx :alchemist/version version)
           (assoc tx :alchemist/description description)
           (assoc tx :alchemist/hash hash)])))

(defn commit
  [conn version description transaction hash]
  (log/infof "committing: %s %s" version description)
      (log/debugf "raw transaction: %s" (pprn-str transaction))
      @(d/transact conn (annotate-transaction version description hash transaction)))

(defn find-schema-attribute
  [db attribute]
  (if-let [eid  (ffirst (q '[:find ?e :in $ ?a :where [?e :db/ident ?a]] db attribute))]
    (d/entity db eid)))

(defn schema-matches?
  [schema entity]
  (let [schema (dissoc schema :db/id :db.install/_attribute)
        keys (keys schema)]
    (log/debugf "checking schema: %s against entity %s" (pprn-str schema) (pprn-str entity))
    (boolean
      (and entity
           (= schema (select-keys entity keys))))))

(defn alchemist-schema-installed?
  [db]
  (log/debug "verifying schema installed")
  (boolean (some (fn [schema]
                  (let [entity (find-schema-attribute db (:db/ident schema))]
                    (schema-matches? schema entity)))
                alchemist-schema)))


(defn ensure-alchemist-schema-installed
  [conn install-if-missing?]
  (let [db (current conn)]
    (if (alchemist-schema-installed? db)
      (log/info "alchemist schema is installed")
      (if install-if-missing?
        (install-alchemist-schema conn)
        (throw (Exception. "alchemist schema must be installed!"))))))
