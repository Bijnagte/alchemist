(ns alchemist-test
  (:require [alchemist :refer :all]
            [midje.sweet :refer :all]
            [datomic.api :as d :refer (q)]))

(def test-uri "datomic:mem://testdb")

(with-state-changes [(before :facts (d/delete-database test-uri))]
  (fact "transmute with defaults succeeds"
        (boolean (transmute test-uri))
        =>  true)
  
  (fact "transmute with update disabled fails"
        (transmute test-uri (assoc default-config :update? false))
        => (throws Exception)))