; Copyright © 2014, Dylan Bijnagte All Rights Reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
; which can be found in the file LICENSE.txt in this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns alchemist.util
  (:require [clojure.string :as string]
            [clojure.pprint :as pprint])
  (:import (java.io StringWriter)))

(defn pprn-str
  [x] 
  (let [w (StringWriter.)]
    (pprint/pprint x w)
    (.toString w)))


(defn version-comparator
  [a b]
  (loop [a (string/split a #"[\.]") b (string/split b #"[\.]")]
    (let [c (compare (first a) (first b))]
      (if (or
            (not= 0 c)
            (and (empty? a) (empty? b)))
        c
        (recur (rest a) (rest b))))))

(defn higher-version?
  [a b]
  (= 1 (version-comparator a b)))

(defn matches-any?
  [regexes string]
  (boolean (some #(re-matches % string) regexes)))

(defn path-matches-any?
  [regexes file]
  (->> file
    .getPath
    (matches-any? regexes)))

(defn format-version
  [version]
  (string/replace version #"[_]" "."))

(defn- get-temp-id
  [element]
  (.idx (:db/id element)))

(defn relativize-temp-ids "Replaces temp ids in a transaction with values based on
there position in the transaction. Ids that are referenced multiple times will have
the same relative id"
  [transaction]
  (loop [elements transaction
         ids {}
         index 0
         result []]
    (if-let [element (first elements)]
      (let [temp-id (get-temp-id element)
            relative-id (or 
                          (get ids temp-id)
                          index)]
        (recur (rest elements)
               (assoc ids temp-id relative-id)
               (inc index)
               (conj result (assoc element :db/id relative-id))))
      result)))

(defn hash-transaction "Returns the hash of the transaction with temp-ids replaced
with relative values so the results are consistent."
  [tx]
  (hash (relativize-temp-ids tx)))