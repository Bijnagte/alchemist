(ns alchemist.util
  (:require [clojure.string :as string]))

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