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
