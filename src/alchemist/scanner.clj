; Copyright © 2014, Dylan Bijnagte All Rights Reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://www.eclipse.org/legal/epl-v10.html)
; which can be found in the file LICENSE.txt in this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns alchemist.scanner
  (:require [clojure.java.classpath :as cp]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [alchemist.util :refer (matches-any?
                                     path-matches-any?
                                     format-version)])
  (:import (java.io File)
           (java.util.jar JarFile JarEntry)))


(def version-portion "[vV](\\d+[\\._0-9]*?)_{2}(\\w+)\\.{1}(edn|clj)")
(def file-pattern (re-pattern (str "\\S*[/]{1}" version-portion)))

(defn read-transaction
  [string]
  (edn/read-string
    {:readers {'db/id datomic.db/id-literal
               'db/fn datomic.function/construct
               'base64 datomic.codec/base-64-literal}}
    string))

(defn make-transmutation
  [resource]
  (let [[_ v d t] (re-matches file-pattern (.getPath resource))]
    {:alchemist/version (format-version v)
     :alchemist/description d
     :dynamic false
     :transaction-fn  #(read-transaction (slurp resource))}))

(defn transmutation-patterns
  [dirs]
  (map #(re-pattern (str "\\S*[/]?" %  "[/]{1}" version-portion)) dirs))

(defn transmutations-in-jar
  [^JarFile jar-file transmutation-patterns]
  (->>
    (cp/filenames-in-jar jar-file)
    (filter #(matches-any? transmutation-patterns %))
    (map clojure.java.io/resource)))

(defn transmutations-in-dir
  [^File dir transmutation-patterns]
  (->> 
    (file-seq dir)
    (filter #(path-matches-any? transmutation-patterns %))))

(defn scan-location
  [transmutation-patterns file]
  (log/infof "scanning %s for transmutations" (.getName file))
  (if (cp/jar-file? file) 
    (transmutations-in-jar (JarFile. file) transmutation-patterns)
    (transmutations-in-dir file transmutation-patterns)))

(defn scan
  ([config]
    (let [{:keys [cp-excludes parent-directories]} config
          transmutation-patterns (transmutation-patterns parent-directories)]
      (scan cp-excludes transmutation-patterns)))
  ([cp-excludes transmutation-patterns]
    (log/info "starting scan of classpath for transmutations")
    (->>
      (cp/classpath)
      (filter #(not (path-matches-any? cp-excludes %)))
      (map #(scan-location transmutation-patterns %))
      flatten
      (map make-transmutation))))
