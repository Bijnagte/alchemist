(ns alchemist-test
  (:require [alchemist :refer :all]
            [alchemist.db :as db]
            [midje.sweet :refer :all]
            [datomic.api :as d :refer (q)]))

(def test-uri "datomic:mem://testdb")
(def tx-1 [{:db/id #db/id[:db.part/db]
           :db/ident :new/attribute
           :db/valueType :db.type/string
           :db/cardinality :db.cardinality/one
           :db.install/_attribute :db.part/db}])
(def transmutation-1 {:alchemist/version "20.0"
                      :dynamic? true
                      :alchemist/description "added"
                      :transaction-fn (fn [] tx-1)})

(facts "about transmute"
       (with-state-changes [(before :facts (d/delete-database test-uri))]
         
         (fact "it succeeds with defaults"
               (boolean (transmute test-uri))
               =>  true)
         
         (fact "it fails with update disabled and new db"
               (transmute test-uri (assoc default-config :update? false))
               => (throws Exception))
         
         (fact "it succeeds with update disabled and there are no transmutations"
               
               (do
                 (d/create-database test-uri)
                 (db/install-alchemist-schema (d/connect test-uri))
                 (transmute test-uri {:update? false :scan? false }))
               => nil?)
         
         (fact "it fails with create disabled and new db"
               (transmute test-uri (assoc default-config :create? false))
               => (throws Exception))
         
         (fact "it succeeds with create disabled and existing db"
               (do (d/create-database test-uri)
                 (boolean (transmute test-uri (assoc default-config :create? false))))
               => true)))

(fact "running twice with same transmutations doesn't change db"
      (do
        (d/delete-database test-uri)
        (d/create-database test-uri)
        (let [conn (d/connect test-uri)
              first-result (transmute conn)
              db-after-first  #_(d/db conn)(:db-after (last first-result))
              first-history (db/find-transmutations db-after-first)]
          (fact
            (count first-result)
            => 2)

          (let [second-result (transmute conn)
                db-after-second (d/db conn)
                second-history (db/find-transmutations db-after-second)]
            
            (fact 
              (= first-history second-history)
              => true)
            
            (fact
              (= first-result second-result)
              => false)
            
            (fact
              (count second-result)
              => 0))))
      (d/delete-database test-uri))

(facts "abount running twice with a new transmutation"
       (do
         (d/delete-database test-uri)
         (d/create-database test-uri)
         (let [conn (d/connect test-uri )
               first-result (transmute conn default-config)
               db-after-first (:db-after (last first-result))
               first-history (db/find-transmutations db-after-first)]
           (fact
             (count first-result)
             => 2)
           (fact
             (count first-history)
             => 2)
           
           (let [second-result (transmute conn default-config (seq [transmutation-1]))
                 db-after-second (:db-after (last second-result))
                 second-history (db/find-transmutations db-after-second)]
             
             (fact
               (count second-result)
               => 1)
             
             (fact
               (count second-history)
               => 3))))
       (d/delete-database test-uri))

(facts "about find-highest-version"
       (fact
         (find-highest-version [{:alchemist/version "1.0"} 
                                {:alchemist/version "1.1"} 
                                {:alchemist/version "0.9"}])
         => "1.1")
       
       (fact
         (find-highest-version [{:alchemist/version "1.0"} 
                                 {:alchemist/version "1.1"} 
                                 {:alchemist/version "20.0"}])
         => "20.0")
       
       (fact "it returns nil if nil is passed"
             (find-highest-version nil)
             => nil)
       
       (fact "it tolerates missing versions"
             (find-highest-version [{:a "1.0"} 
                                    {:alchemist/version "0.9"}])
             => "0.9"))

(facts "about split-at-version"
       (let [transmutations [{:alchemist/version "1.0"} 
                                {:alchemist/version "1.1"} 
                                {:alchemist/version "0.9"}
                                {:alchemist/version "0.9.1"}
                                {:alchemist/version "20.0"}]
             [higher at-or-below] (split-at-version "0.9.1" transmutations)]
         
         (facts "about at or below"
           
           (some #{{:alchemist/version "20.0"}} at-or-below)
           => falsey
                
           (count at-or-below)
           => 2
           
           (some #{{:alchemist/version "0.9"}} at-or-below)
           => truthy
           
           (some #{{:alchemist/version "0.9.1"}} at-or-below)
           => truthy)
         
         (facts "about higher"
           (count higher)
           => 3
           
           (some #{{:alchemist/version "1.1"}} higher)
           => truthy
           
           (some #{{:alchemist/version "1.0"}} higher)
           => truthy)))

(fact "sort-by-version orders as expected"
       (sort-by-version [{:alchemist/version "1.0" :b1 "b"} 
                         {:alchemist/version "1.1"}
                         {:alchemist/version "20.0"}
                         {:alchemist/version "0.9" :a "a"}
                         {:alchemist/version "0.9.1"}])
       => [{:alchemist/version "0.9" :a "a"} 
           {:alchemist/version "0.9.1"} 
           {:alchemist/version "1.0" :b1 "b"}
           {:alchemist/version "1.1"}
           {:alchemist/version "20.0"}])