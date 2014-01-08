(ns alchemist.util-test
  (:use midje.sweet
        alchemist.util))

(tabular
  (fact "versions compare correctly"
        (version-comparator ?version-a ?version-b) => ?expected)
  ?version-a    ?version-b    ?expected
  "0"           "1"           -1
  "1"           "0"            1
  "1"           "1"            0
  "0.1"         "0.2"         -1
  "0.19"        "0.2"         -1
  "0.2"         "0.2"          0
  "0.20"        "0.2"          1
  "0.2.1"       "0.2.0"        1
  "0.2a"        "0.2b"        -1
  "0.1.2.3.4.5.6.7.8.9.1" "0.1.2.3.4.5.6.7.8.9.0" 1
  )