(ns alchemist.db-test
  (:require [alchemist.db :refer :all]
            [midje.sweet :refer :all]
            [datomic.api :as d :refer (q)]))

(def test-uri "datomic:mem://testdb")

(with-state-changes [(before :facts (do
                                      (d/delete-database test-uri)
                                      (d/create-database test-uri)))
                     (after :facts (d/delete-database test-uri))]
  
  (facts "about ensure-alchemist-schema-installed?"
         (fact "it is false on a new db"
               (let [conn (d/connect test-uri)]
                 (alchemist-schema-installed? (d/db conn))
                 => false))
         
         (fact "it is true once the schema is installed"
               (let [conn (d/connect test-uri)]
                 (install-alchemist-schema conn)
                 (alchemist-schema-installed? (d/db conn)))
               => true))
  
  (facts "about install-schema"
         (fact "it returns something"
           (boolean (let [conn (d/connect test-uri)]
                     (install-alchemist-schema conn)))
           => true) 
  ))

