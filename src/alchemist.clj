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
    (db/commit conn version description transaction hash)))

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


(defn matches-history?
  [transmutation tx-entity]
  (boolean
    (and transmutation
         (= (:alchemist/version transmutation)
            (:alchemist/version tx-entity))
         (or 
           (:dynamic? transmutation)
           (= (hash-transaction ((:transaction-fn transmutation)))
              (:alchemist/hash tx-entity))))))

(defn same-transmutations?
  [run history]
  (log/infof "verifying %d transmutations" (count run))
  (if-not (= (count run) (count history))
    false ;;fail fast
    (loop [run (sort-by-version run) history (sort-by-version history)]
      (if (and (empty? run)
               (empty? history))
        true
        (let [transmutation (first run)
              tx-entity (first history)]
          (log/debugf "comparing run: %s transmutation history: %s"
                      (pprn-str transmutation) (pprn-str tx-entity))
          (if (matches-history? transmutation tx-entity)
            (do
              (log/infof "verified version %s" (:alchemist/version transmutation))
              (recur (rest run) (rest history)))
            (do
              (log/errorf
                "verification failed! transmutation run: %s transmutation history: %s"
                (pprn-str transmutation) (pprn-str tx-entity))
              false)))))))

(defn handle-run
  [conn verify? transmutations]
  (let [history (db/transmutation-history conn)]
    (if (empty? history)
      transmutations
      (let [version (current-version history)
            [new run] (split-at-version version transmutations)]
        (when verify?
          (when-not (same-transmutations? run history)
            (throw (Exception. "transmutation verification failed!"))))
        new))))

(defn check-schema
  [conn update?]
  (let [db (db/current conn)]
    (if-not (db/alchemist-schema? db)
      (if update?
        (db/install-alchemist-schema conn)
        (throw (Exception. "alchemist schema missing!")))
      (log/info "alchemist schema already installed"))))

(defn transmute
  ([uri] (transmute uri default-config))
  ([uri config](transmute uri config nil))      
  ([uri config transmutations]
    (let [{:keys [create? scan? verify? update? parent-directories cp-excludes]} config
          conn (db/connect uri create?)]
      (check-schema conn update?)
      (let [transmutations (-> (if scan? 
                                 (into (scanner/scan parent-directories cp-excludes)
                                       transmutations)
                                 transmutations)
                             sort-by-version)]
        (log/debugf "transmutations: %s" (pprn-str transmutations))
        (let [transmutations (handle-run conn verify? transmutations)]
          (when update?
            (run-transmutations conn transmutations)))))))