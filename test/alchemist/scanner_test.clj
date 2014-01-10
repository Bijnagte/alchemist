(ns alchemist.scanner-test
  (:require [alchemist.util :refer (matches-any?)])
  (:use clojure.test
        alchemist.scanner)
  (:import (java.util.regex Pattern)))

(deftest test-transmutation-patterns
  (let [patterns (transmutation-patterns ["root" "other"])]
    (doseq [pattern  patterns]
      (is (instance? Pattern pattern)))
    
    (are [path] (matches-any? patterns path)
         "src/file/root/v1__Desc.edn"
         "src/file/root/v1.1__Desc.edn"
         "src/file/other/v1_1__Desc.edn"
         "/other/v1__Desc.edn"
         "other/v1__Desc.edn")
    
    (are [path] (not (matches-any? patterns path))
         "src/file/root/v1_1__Desc.ed"
         "src/file/root/v1_1_Desc.edn"
         "src/file/root/1_1_Desc.edn"
         "src/file/root/a/v1_1_Desc.edn")))

