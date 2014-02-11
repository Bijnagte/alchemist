(ns alchemist.util-test
  (:require [datomic.api])
  (:use midje.sweet
        alchemist.util))


(facts "about version-comparator"  
  (tabular
   (fact "versions compare as expected"
         (version-comparator ?version-a ?version-b) => ?expected)
   ?version-a    ?version-b    ?expected
   "0"           "1"            neg?
   "1"           "0"            pos?
   "1"           "1"            0
   "0.1"         "0.2"          neg?
   "0.19"        "0.2"          neg?
   "0.2"         "0.2"          0
   "0.20"        "0.2"          pos?
   "0.2.1"       "0.2.0"        pos?
   "0.2a"        "0.2b"         neg?
   "20.0"        "1.1"          pos?
   "20.0"        "0.9.1"        pos?
   "0.1.2.3.4.5.6.7.8.9.1" "0.1.2.3.4.5.6.7.8.9.0" 1
   )
  (fact "it handles nil gracefully"
    (version-comparator "1.0" nil)
    => pos?
    
    (version-comparator nil "1.0")
    => neg?
    
    (version-comparator nil nil)
    => 0))

(tabular
  (fact "higher-version returns boolean"
        (higher-version? ?version-a ?version-b) => ?expected)
  ?version-a    ?version-b    ?expected
  "0"           "1"           false
  "1"           "0"           true
  "1"           "1"           false
  "0.2"         "0.2"         false
  "0.20"        "0.2"         true
  "0.2.1"       "0.2.0"       true
  "0.2a"        "0.2b"        false
  "20.0"        "1.1"         true
  "20.0"        "0.9.1"       true
  "0.1.2.3.4.5.6.7.8.9.1" "0.1.2.3.4.5.6.7.8.9.0" true
  )

 (fact "pprn-str writes a map to a string with new lines"
       (pprn-str {:a "valueaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                  :b 0
                  :c 2}) => 
 "{:a \"valueaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",
 :c 2,
 :b 0}\n")
 
 (tabular
  (fact "_ in version is replaced with ."
        (format-version ?version) => ?expected)
  ?version ?expected
  "0_1_0"  "0.1.0"
  "1.0_"   "1.0."
  "a_1-b"  "a.1-b"
  )
 
 (facts "relativize-temp-ids uses index unless except when referenced multiple times"
       (let [transaction [{:db/id #db/id[:db.part/db ]}
                          {:db/id #db/id[:db.part/db -1]}
                          {:db/id #db/id[:db.part/db]}
                          {:db/id #db/id[:db.part/db  -1]}
                          {:db/id #db/id[:db.part/db]}]
             nth-id (fn [vector pos]
                      (:db/id (nth vector pos)))]
         (nth-id (relativize-temp-ids transaction) 0) => 0
         (nth-id (relativize-temp-ids transaction) 1) => 1
         (nth-id (relativize-temp-ids transaction) 2) => 2
         (nth-id (relativize-temp-ids transaction) 3) => 1
         (nth-id (relativize-temp-ids transaction) 4) => 4
         ))
 
 (facts "about transaction hashes"
        (let [transaction-1 [{:db/id #db/id[:db.part/db]}
                            {:db/id #db/id[:db.part/db]}]
              transaction-2 [{:db/id #db/id[:db.part/db]}
                            {:db/id #db/id[:db.part/db]}]]
        
          (fact "reader generated temp-ids cause hashes to be different" 
            (= 
              (hash transaction-1)
              (hash transaction-2))
            => false)
        
          (fact "when they are made relative the hashes are the same"
            (= (hash-transaction transaction-1)
               (hash-transaction transaction-2))
            => true))
        
        (let [transaction-3 [{:db/id #db/id[:db.part/db]
                              :db/ident :test/attribute
                              :db/valueType :db.type/string
                              :db/cardinality :db.cardinality/one
                              :db.install/_attribute :db.part/db}]
              transaction-4 [{:db/valueType :db.type/string
                              :db/id #db/id[:db.part/db]
                              :db/ident :test/attribute
                              :db.install/_attribute :db.part/db
                              :db/cardinality :db.cardinality/one}]]

          (fact "key order doesn't matter in the hash"
                (= (hash-transaction transaction-3)
                   (hash-transaction transaction-4))
                => true)))
        