; Copyright © 2014, Dylan Bijnagte All Rights Reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
; which can be found in the file LICENSE.txt in this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns alchemist
  (:require [alchemist.db :as db]
            [clojure.tools.logging :as log]
            [alchemist.scanner :as scanner]
            [alchemist.util :refer (version-comparator
                                     higher-version?
                                     pprn-str
                                     hash-transaction)]))

(def default-config {:create? true
                     :scan? true
                     :verify? true
                     :update? true
                     :parent-directories scanner/parent-directories
                     :cp-excludes scanner/cp-excludes})

(defn run-transmutations
  [conn transmutations]
  (log/infof "running %d transmutations" (count transmutations))
  (for [t transmutations
        :let [{version :alchemist/version} t
              {description :alchemist/description} t
              transaction ((:transaction-fn t))
              hash (hash-transaction transaction)]]
    (do
      (log/infof "commiting %s %s" version description)
      (db/commit conn version description transaction hash))))

(defn sort-by-version
  [transmutations]
  (sort
    (fn [a b] 
      (version-comparator (:alchemist/version a) (:alchemist/version b)))
    transmutations))

(defn find-highest-version
  [entities]
  (-> 
    entities
    sort-by-version
    last
    :alchemist/version))

(defn split-at-version
  [version transmutations]
  (loop [transmutations transmutations
        result [(list) (list)]]
   (if-let [t (first transmutations)]
     (let [higher? (higher-version? (:alchemist/version t) version)
           [higher at-or-below] result
           new-result (if higher?
                        [(conj higher t) at-or-below]
                        [higher (conj at-or-below t)])]
       (recur (rest transmutations) new-result))
     result)))

(defn matches-history?
  [transmutation tx-entity]
  (boolean
    (and (map? transmutation)
         (= (:alchemist/version transmutation)
            (:alchemist/version tx-entity))
         (or 
           (:dynamic? transmutation)
           (= (hash-transaction ((:transaction-fn transmutation)))
              (:alchemist/hash tx-entity))))))

(defn verify-transmutations
  [below-current history]
  (log/infof "verifying %d transmutations" (count below-current))
  (loop [run (sort-by-version below-current) history (sort-by-version history)]
    (when-not (every? empty? [run history])
      (let [transmutation (first run)
            tx-entity (first history)]
        (log/debugf "comparing run: %s transmutation history: %s"
                    (pprn-str transmutation) (pprn-str tx-entity))
        (if (matches-history? transmutation tx-entity)
          (do
            (log/infof "verified version %s" (:alchemist/version transmutation))
            (recur (rest run) (rest history)))
          (throw (Exception. (format
                               "verification failed! transmutation run: %s transmutation history: %s"
                               (pprn-str transmutation) (pprn-str tx-entity)))))))))

(defn handle-run
  [conn verify? transmutations]
  (let [history (db/transmutation-history conn)]
    (log/debugf "found %d historical transmutations" (count history))
    (if (empty? history)
      transmutations
      (let [current-version (find-highest-version history)
            [new below-current] (split-at-version current-version transmutations)]
        (log/debugf "found %d new and %d below version %s" (count new) (count below-current) current-version)
        (when verify?
          (verify-transmutations below-current history))
        new))))

(defn assemble-transmutations
  [{:keys [scan? parent-directories cp-excludes]} provided-transmutations]
  (sort-by-version
    (if scan? 
      (into
        (scanner/scan parent-directories cp-excludes)
        provided-transmutations)
      provided-transmutations)))

(defmulti transmute "The main function of alchemist.
Creates, verifies and updates a db provided by the conn or uri param
using transmutations from provided-transmutations and/or those found
by scanning the classpath"
  {:arglists '( [uri]
                [conn]
                [uri config]
                [conn config]
                [uri config provided-transmutations]
                [conn config provided-transmutations])}
  (fn [first & args] [(class first) (count args)]))

(defmethod transmute [String 0]
  [uri]
  (transmute uri default-config []))

(defmethod transmute [String 1]
  [uri config]
  (transmute uri config []))

(defmethod transmute [String 2]
  [uri config provided-transmutations]
  (transmute (db/connect uri (:create? config)) config provided-transmutations))

(defmethod transmute [datomic.Connection 0]
  [conn]
  (transmute conn default-config []))

(defmethod transmute [datomic.Connection 1]
  [conn config]
  (transmute conn config []))

(defmethod transmute [datomic.Connection 2]
  [conn config provided-transmutations]
  (when-let [transmutations (assemble-transmutations config provided-transmutations)]
    (let [{:keys [verify? update?]} config]
      (log/tracef "transmutations: %s" (pprn-str transmutations))
      (db/ensure-alchemist-schema-installed conn update?)
      (let [new-transmutations (handle-run conn verify? transmutations)]          
        (when update?
          (run-transmutations conn new-transmutations))))))
