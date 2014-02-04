(ns alchemist-test
  (:require [alchemist :refer :all]
            [midje.sweet :refer :all]
            [datomic.api :as d :refer (q)]))

(def test-uri "datomic:mem://testdb")

(facts "about transmute"
       (with-state-changes [(before :facts (d/delete-database test-uri))]
         
         (fact "it succeeds with defaults"
               (boolean (transmute test-uri))
               =>  true)
         
         (fact "it fails with update disabled and new db"
               (transmute test-uri (assoc default-config :update? false))
               => (throws Exception))
         
         (fact "it succeeds with update disabled and there are no transmutations"
               (transmute test-uri {:update? false :scan? false })
               => nil?)
         
         
         (fact "it fails with create disabled and new db"
               (transmute test-uri (assoc default-config :create? false))
               => (throws Exception))
         
         (fact "it succeeds with create disabled and existing db"
               (do (d/create-database test-uri)
                 (boolean (transmute test-uri (assoc default-config :create? false))))
               => true)))