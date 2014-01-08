(defproject alchemist "0.1.0-SNAPSHOT"
  :description "Database migrations for datomic"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/java.classpath "0.2.1"]
                 [com.datomic/datomic-free "0.9.4384"
                  :exclusions [org.slf4j/slf4j-nop org.slf4j/slf4j-log4j12]]
                 [org.clojure/tools.logging "0.2.6"]]
  :profiles {:dev {:resource-paths ["dummy-data"]
                   :dependencies [[midje "1.6.0"]
                                  [ch.qos.logback/logback-classic "1.0.1"]
                                  [org.codehaus.groovy/groovy "2.2.1"]]}})
