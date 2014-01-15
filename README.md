alchemist
=========
A Clojure library to manage database migrations for [Datomic](http://www.datomic.com/)

Transmutations are versioned transactions that are committed to the database in order. As Alchemist commits the transaction it is annotated with :alchemist/version, :alchemist/description, and :alchemist/hash attributes.
When Alchemist is run again it will not re-commit the transactions below the current state. 

Example Usage
-------------

Create properly named [edn](https://github.com/edn-format/edn) files into the transmutations directory:
```
MyProject
	resources/
		transmutations/
			v1_0_0__initial_commit.edn
			v1_1_0__more_schema.edn
```
containing transaction data:
```clojure
[{:db/id #db/id[:db.part/db]
  :db/ident :sample/attribute
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db.install/_attribute :db.part/db}
  
  {:db/id #db/id[:db.part/db]
  :db/ident :sample/attribute2
  :db/valueType :db.type/string
  :db/cardinality :db.cardinality/one
  :db.install/_attribute :db.part/db}
  ]
```

and run with the defaults:
```clojure
(require '[alchemist])

(alchemist/transmute "datomic:mem://testdb")
```
### alchemist schema ###
alchemist keeps track of which transmutations have already run by annotating the transactions
```clojure
{:alchemist/version "1.0.0"
 :alchemist/description "initial_commit"
 :alchemist/hash 236679938}
```

and will not transact them a second time so feel free to run at app start-up every time.

transmutation
--------------
### trans·mu·ta·tion ###
transmyo͞oˈtāSHn

*noun*

1. the action of changing or the state of being changed into another form
2. a function, wrapped in a map, that produces a Datomic transaction vector

```clojure
{:alchemist/version "1.0.0a"
 :alchemist/description "initial_schema"
 :dynamic? true
 :transaction-fn my-transaction-producing-function}
```
__:alchemist/version__
A string containing "**.**" separated elements. Each element will be compared independently from left to right. Versions will be transacted in ascending order.

__:alchemist/description__
A description that will be annotated on the transaction.

__:dynamic?__
If true then the transaction vector's hash will not be compared during verification.
If false and the transaction vector's hash changes between runs then verification will fail.

*Note: the __#db/id__ reader macro produces variable results so the __:db/id__ keys will be replaced with relative values prior to getting the hash so that the numbers are consistent.*

__:transaction-fn__
If alchemist produces the transmutation through classpath scanning the function will read the file lazily. Any function that produces transaction edn is valid.


Configuration
-------
```clojure
{:create? true
 :verify? true
 :update? true
 :scan? true
 :parent-directories ["transmutations"]
 :cp-excludes [#".*/?jre/\S*.jar"
               #".*/?clojure\S*.jar"
               #".*/?datomic\S*.jar"
               #".*/?bin"]}
```
__:create?__
boolean, allows for creation of a database if it is not found at the provided uri

__:verify?__
boolean, will cause an exception to be thrown if the transmutations up to the current version of the database do not match those found in database

__:update?__
boolean, allows previously not run transmutations to be committed

__:scan?__
boolean, enables classpath scanning to discover transmutation files

__:parent-directories__
a vector of parent directory names to scan for transmutations

__:cp-excludes__
a vector of regexes to filter the classpath prior to scanning

```clojure
(require '[alchemist :as a])

(a/transmute "datomic:mem://testdb"
             (merge a/default-config 
                    {:create? false
                     :parent-directories ["transmutations" "sample_data"]}))
```
License
-------
Copyright © 2013 Dylan Bijnagte

Distributed under the Eclipse Public License, the same as Clojure.