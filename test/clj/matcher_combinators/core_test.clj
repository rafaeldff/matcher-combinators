(ns matcher-combinators.core-test
  (:require [midje.sweet :refer :all :exclude [exactly contains] :as sweet]
            [clojure.test :refer [deftest testing is]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.string :as str]
            [orchestra.spec.test :as spec.test]
            [matcher-combinators.clj-test]
            [matcher-combinators.core :as core :refer :all]
            [matcher-combinators.matchers :as matchers :refer :all]
            [matcher-combinators.model :as model]
            [matcher-combinators.result :as result]
            [matcher-combinators.dispatch :as dispatch]
            [matcher-combinators.parser]))

(spec.test/instrument)

(facts "on the leaf values matcher: v"
  (match (equals 42) 42) => {::result/type   :match
                             ::result/value  42
                             ::result/weight 0}
  (match (equals 42) 43) => {::result/type   :mismatch
                             ::result/value  (model/->Mismatch 42 43)
                             ::result/weight 1})

(fact "on map matchers"
  (tabular
    (facts "on common behaviors among all map matchers"
      (fact "matches when given a map with matching values for every key"
        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 42, :b 1337})
        => {::result/type   :match
            ::result/value  {:a 42, :b 1337}
            ::result/weight 0})

      (fact "when actual values fail to match expected matchers for
            corresponding keys, mismatch marking each value Mismatch"
        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 43, :b 1337})
        => {::result/type   :mismatch
            ::result/value  {:a (model/->Mismatch 42 43), :b 1337}
            ::result/weight 1}

        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 42, :b 13373})
        => {::result/type   :mismatch
            ::result/value  {:a 42, :b (model/->Mismatch 1337 13373)}
            ::result/weight 1}

        (match (?map-matcher {:a (equals 42), :b (equals 1337)}) {:a 43, :b 13373})
        => {::result/type   :mismatch
            ::result/value  {:a (model/->Mismatch 42 43), :b (model/->Mismatch 1337 13373)}
            ::result/weight 2})

      (fact "when actual input map doesn't contain values for expected keys,
            mismatch marking each key with a Missing value"
        (match (?map-matcher {:a (equals 42)}) {})
        => {::result/type   :mismatch
            ::result/value  {:a (model/->Missing 42)}
            ::result/weight 1}

        (match (?map-matcher {:a (equals 42) :b (equals 42)}) {:b 42})
        => {::result/type   :mismatch
            ::result/value  {:b 42, :a (model/->Missing 42)}
            ::result/weight 1})

      (tabular
        (fact "mismatch when given an actual input that is not a map"
          (match (?map-matcher {:a (equals 1)}) ?actual)
          => {::result/type   :mismatch
              ::result/value  (model/->Mismatch {:a (equals 1)} ?actual)
              ::result/weight 1})
        ?actual
        1
        "a1"
        [[:a 1]]))
    ?map-matcher
    embeds
    equals)

  (facts "on the equals matcher for maps"
    (fact "when the actual input map contains keys for which there are no
          corresponding matchers specified, mismatch marking each key with an
          Unexpected value"
      (match (equals {:a (equals 42)}) {:a 42 :b 1337})
      => {::result/type   :mismatch
          ::result/value  {:a 42, :b (model/->Unexpected 1337)}
          ::result/weight 1}

      (match (equals {:a (equals 42)}) {:b 42})
      => {::result/type   :mismatch
          ::result/value  {:b (model/->Unexpected 42), :a (model/->Missing 42)}
          ::result/weight 2})))

(facts "on sequence matchers"
  (tabular
    (facts "on common behaviors among all sequence matchers"
      (fact "matches when actual sequence elements match each matcher, in order and in total"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})])
          [{:id 1, :a 1} {:id 2, :a 2}])
        => {::result/type   :match
            ::result/value  [{:id 1, :a 1} {:id 2, :a 2}]
            ::result/weight 0})

      (fact "mismatch when none of the expected matchers is a match for one
             element of the given sequence"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})])
          [{:id 1 :a 1} {:id 2 :a 200}])
        => (just {::result/type   :mismatch
                  ::result/value  anything
                  ::result/weight number?}))

      (fact "only matches when all expected matchers are matched by elements of
             the given sequence"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})
                                   (equals {:id (equals 3) :a (equals 3)})])
          [{:id 1 :a 1} {:id 2 :a 2}])
        => (just {::result/type   :mismatch
                  ::result/value  anything
                  ::result/weight number?}))

      (fact "only matches when all of the input sequence elements are matched
             by an expected matcher"
        (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                   (equals {:id (equals 2) :a (equals 2)})])
          [{:id 1 :a 1} {:id 2 :a 2} {:id 3 :a 3}])
        => (just {::result/type   :mismatch
                  ::result/value  anything
                  ::result/weight number?}))

      (tabular
        (fact "mismatches when the actual input is not a sequence"
          (match (?sequence-matcher [(equals {:id (equals 1) :a (equals 1)})
                                     (equals {:id (equals 2) :a (equals 2)})]) ?actual)
          => {::result/type   :mismatch
              ::result/value  (model/->Mismatch [(equals {:id (equals 1) :a (equals 1)})
                                                 (equals {:id (equals 2) :a (equals 2)})]
                                                ?actual)
              ::result/weight 1})
        ?actual
        12
        "12"
        '12
        :12
        {:x 12}
        #{1 2}))

    ?sequence-matcher
    equals
    in-any-order)

  (facts "on the equals matcher for sequences"
    (fact "on element mismatches, marks each mismatch"
      (match (equals [(equals 1) (equals 2)]) [2 1])
      => {::result/type   :mismatch
          ::result/value  [(model/->Mismatch 1 2) (model/->Mismatch 2 1)]
          ::result/weight 2}

      (match (equals [(equals 1) (equals 2)]) [1 3])
      => {::result/type   :mismatch
          ::result/value  [1 (model/->Mismatch 2 3)]
          ::result/weight 1})

    (fact "mismatch reports elements in correct order"
      (match (equals [(equals 1) (equals 2) (equals 3)])
        (list 1 2 4))
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Mismatch 3 4)]
          ::result/weight 1})

    (fact "when there are more elements than expected matchers, mark each extra element as Unexpected"
      (match (equals [(equals 1) (equals 2)]) [1 2 3])
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Unexpected 3)]
          ::result/weight 1})

    (fact "Mismatch plays well with nil"
      (match (equals [(equals 1) (equals 2) (equals 3)]) [1 2 nil])
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Mismatch 3 nil)]
          ::result/weight 1})

    (fact "when there are more matchers then actual elements, append the expected values marked as Missing"
      (match (equals [(equals 1) (equals 2) (equals 3)]) [1 2])
      => {::result/type   :mismatch
          ::result/value  [1 2 (model/->Missing 3)]
          ::result/weight 1}

      (match (equals [(equals {:a (equals 1)}) (equals {:b (equals 2)})]) [{:a 1}])
      => {::result/type   :mismatch
          ::result/value  [{:a 1} (model/->Missing {:b (equals 2)})]
          ::result/weight 1}))

  (facts "on the in-any-order sequence matcher"
    (tabular
      (facts "common behavior for all in-any-order arities"
        (fact "matches a sequence with elements corresponding to the expected matchers, in different orders"
          (match
           (?in-any-order-matcher [(equals {:id (equals 1) :x (equals 1)})
                                   (equals {:id (equals 2) :x (equals 2)})])
            [{:id 2 :x 2} {:id 1 :x 1}])
          => {::result/type   :match
              ::result/value  [{:id 2 :x 2} {:id 1 :x 1}]
              ::result/weight 0}

          (match
           (?in-any-order-matcher [(equals {:id (equals 1) :x (equals 1)})
                                   (equals {:id (equals 2) :x (equals 2)})
                                   (equals {:id (equals 3) :x (equals 3)})])
            [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}])
          => {::result/type   :match
              ::result/value  [{:id 2 :x 2} {:id 1 :x 1} {:id 3 :x 3}]
              ::result/weight 0}))
      ?in-any-order-matcher
      in-any-order)

    (facts "the 1-argument arity has a simple all-or-nothing behavior:"
      (fact "in-any-order for list of same value/matchers"
        (match (in-any-order [(equals 2) (equals 2)]) [2 2])
        => {::result/type   :match
            ::result/value  [2 2]
            ::result/weight 0})

      (fact "when there the matcher and list count differ, mark specific mismatches"
        (match (in-any-order [(equals 1) (equals 2)]) [1 2 3])
        => (just {::result/type   :mismatch
                  ::result/value  (just [1 2 (model/->Unexpected 3)]
                                        :in-any-order)
                  ::result/weight 1})

        (match (in-any-order [(equals 1) (equals 2) (equals 3)]) [1 2])
        => (just {::result/type   :mismatch
                  ::result/value  (just [1 2 (model/->Missing 3)]
                                        :in-any-order)
                  ::result/weight 1})))))

(facts "on nesting multiple matchers"
  (facts "on nesting equals matchers for sequences"
    (match
     (equals [(equals [(equals 1) (equals 2)]) (equals 20)])
      [[1 2] 20])
    => {::result/type   :match
        ::result/value  [[1 2] 20]
        ::result/weight 0}

    (match
     (equals [(equals [(equals 1) (equals 2)]) (equals 20)])
      [[1 5] 20])
    => {::result/type   :mismatch
        ::result/value  [[1 (model/->Mismatch 2 5)] 20]
        ::result/weight 1}

    (match
     (equals [(equals [(equals 1) (equals 2)]) (equals 20)])
      [[1 5] 21])
    => {::result/type   :mismatch
        ::result/value  [[1 (model/->Mismatch 2 5)] (model/->Mismatch 20 21)]
        ::result/weight 2})

  (fact "sequence type is preserved in mismatch output"
    (-> (equals [(equals [(equals 1)])])
        (match [[2]])
        ::result/value)
    => #(instance? (class (vector)) %)

    (-> (equals [(equals [(equals 1)])])
        (match (list [2]))
        ::result/value)
    => #(instance? (class (list 'placeholder)) %))

  (fact "nesting in-any-order matchers"
    (match
     (in-any-order [(equals {:id (equals 1) :a (equals 1)})
                    (equals {:id (equals 2) :a (equals 2)})])
      [{:id 1 :a 1} {:id 2 :a 2}])
    => {::result/type   :match
        ::result/value  [{:id 1 :a 1} {:id 2 :a 2}]
        ::result/weight 0})

  (facts "nesting embeds for maps"
    (match
     (embeds {:a (equals 42) :m (embeds {:x (equals "foo")})})
      {:a 42 :m {:x "foo"}})
    => {::result/type   :match
        ::result/value  {:a 42 :m {:x "foo"}}
        ::result/weight 0} (match (embeds {:a (equals 42)
                                           :m (embeds {:x (equals "foo")})})
                             {:a 42
                              :m {:x "bar"}})
    => {::result/type   :mismatch
        ::result/value  {:a 42
                         :m {:x (model/->Mismatch "foo" "bar")}}
        ::result/weight 1}

    (match (embeds {:a (equals 42)
                    :m (embeds {:x (equals "foo")})})
      {:a 43
       :m {:x "bar"}})
    => {::result/type   :mismatch
        ::result/value  {:a (model/->Mismatch 42 43)
                         :m {:x (model/->Mismatch "foo" "bar")}}
        ::result/weight 2})

  (match (equals [(equals {:a (equals 42)
                           :b (equals 1337)})
                  (equals 20)])
    [{:a 42 :b 1337} 20])
  => {::result/type   :match
      ::result/value  [{:a 42 :b 1337} 20]
      ::result/weight 0}

  (match (equals [(equals {:a (equals 42)
                           :b (equals 1337)})
                  (equals 20)])
    [{:a 43 :b 1337} 20])
  => {::result/type   :mismatch
      ::result/value  [{:a (model/->Mismatch 42 43) :b 1337} 20]
      ::result/weight 1})

;; Since the parser namespace needs to be loaded to interpret functions as
;; matchers, and we don't want to load the parser namespce, we need to manually
;; wrap functions in a predicate matcher
(defn- pred-matcher [expected]
  (assert ifn? expected)
  (core/->PredMatcher expected (str expected)))

(fact
 (match (equals [(pred-matcher odd?) (pred-matcher even?)]) [1 2])
  => {::result/type   :match
      ::result/value  [1 2]
      ::result/weight 0}
  (match (equals [(pred-matcher odd?) (pred-matcher even?)]) [1])
  => (just {::result/type   :mismatch
            ::result/value  (just [1 anything])
            ::result/weight 1}))

(let [matchers [(pred-matcher odd?) (pred-matcher even?)]]
  (fact "no matching when there are more matchers than elements"
    (#'core/matches-in-any-order? matchers [] true [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything anything])
                        :matched   empty?})
    (#'core/matches-in-any-order? matchers [1] false [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything])
                        :matched   (just [anything])})
    (#'core/matches-in-any-order? matchers [1] true [])
    => (sweet/contains {:matched?  false
                        :unmatched (just [anything])
                        :matched   (just [anything])}))
  (fact "subset will recur on matchers"
    (#'core/matches-in-any-order? matchers [5 4 1 2] true [])
    => (sweet/contains {:matched?  true
                        :unmatched nil?
                        :matched   (just [anything anything])})
    (#'core/matches-in-any-order? matchers [5 1 3 2] true [])
    => (sweet/contains {:matched?  true
                        :unmatched nil?
                        :matched   (just [anything anything])}))
  (fact "works well with identical matchers"
    (#'core/matches-in-any-order? [(equals 2) (equals 2)] [2 2] false [])
    => (sweet/contains {:matched?  true
                        :unmatched empty?
                        :matched   (just [anything anything])}))
  (fact "mismatch if there are more matchers than actual elements"
    (#'core/match-any-order matchers [5] false)
    => (just {::result/type   :mismatch
              ::result/value  (just [(just (model/->Missing anything)) 5]
                                    :in-any-order)
              ::result/weight 1})
    (#'core/match-any-order matchers [5] true)
    => (just {::result/type   :mismatch
              ::result/value  (just [5 (just (model/->Missing anything))]
                                    :in-any-order)
              ::result/weight 1})))

(tabular
  (fact "matching for absence in map"
    (core/match (?matcher {:a (equals 42)
                           :b absent})
      {:a 42})
    => (just {::result/type   :match
              ::result/value  {:a 42}
              ::result/weight 0})

    (core/match (?matcher {:a (equals 42)
                           :b absent})
      {:a 42
       :b 43})
    => (just {::result/type   :mismatch
              ::result/value  (just {:a 42
                                     :b (just {:actual 43})})
              ::result/weight #(or (= 1 %) (= 2 %))}))
  ?matcher
  equals
  embeds)

(fact "`absent` interaction with keys pointing to `nil` values"
  (core/match (equals {:a (equals 42)
                       :b absent})
    {:a 42
     :b nil})
  => (just {::result/type   :mismatch
            ::result/value  (just {:a 42
                                   :b {:actual nil}})
            ::result/weight 2}))

(fact "using `absent` incorrectly outside of a map"
  (core/match (equals [(equals 42) absent])
    [42])
  => (just {::result/type   :mismatch
            ::result/value  (just [42 {:message "`absent` matcher should only be used as the value in a map"}])
            ::result/weight 1}))

(tabular
  (fact "Providing seq/map matcher with incorrect input leads to automatic mismatch"
    (core/match (?matcher 1) 1)
    => (just {::result/type   :mismatch
              ::result/value  (sweet/contains {:expected-type-msg
                                               #(str/starts-with? % (-> ?matcher var meta :name str))
                                               :provided
                                               "provided: 1"})
              ::result/weight number?}))
  ?matcher
  prefix
  embeds)

(def pred-set #{(pred-matcher odd?) (pred-matcher pos?)})
(def pred-seq [(pred-matcher odd?) (pred-matcher pos?)])

(def short-equals-seq (map equals [1 3]))

(fact "embeds for sequences"
  (core/match (embeds short-equals-seq) [3 4 1]) => (just {::result/type   :match
                                                           ::result/value  (just [3 4 1])
                                                           ::result/weight 0})
  (core/match (embeds short-equals-seq) [3 4 1 5]) => (just {::result/type   :match
                                                             ::result/value  (just [3 4 1 5])
                                                             ::result/weight 0}))

(fact "embeds /set-equals matches"
  (core/match (embeds pred-set) #{1 3}) => (just {::result/type   :match
                                                  ::result/value  (just #{1 3})
                                                  ::result/weight 0})
  (core/match (set-embeds pred-seq) #{1 3}) => (just {::result/type   :match
                                                      ::result/value  (just #{1 3})
                                                      ::result/weight 0})
  (core/match (equals pred-set) #{1 3}) => (just {::result/type   :match
                                                  ::result/value  (just #{1 3})
                                                  ::result/weight 0})
  (core/match (set-equals pred-seq) #{1 3}) => (just {::result/type   :match
                                                      ::result/value  (just #{1 3})
                                                      ::result/weight 0}))

(fact "embeds /equals mismatches due to type"
  (core/match (equals pred-seq) #{1 3})
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   #{1 3}
                                   :expected anything})
            ::result/weight 1})
  (core/match (equals pred-set) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (embeds pred-seq) #{1 3})
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   #{1 3}
                                   :expected anything})
            ::result/weight 1})
  (core/match (embeds pred-set) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (embeds 1) [1])
  => (just {::result/type   :mismatch
            ::result/value  (just {:expected-type-msg #"^embeds *"
                                   :provided          #"^provided: 1"})
            ::result/weight 1}))

(fact "embeds /set-equals mismatches due to type"
  (core/match (set-embeds pred-seq) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (set-equals pred-seq) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:actual   [1 3]
                                   :expected anything})
            ::result/weight 1})
  (core/match (set-embeds 1) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:expected-type-msg #"^set-embeds*"
                                   :provided          #"^provided: 1"})
            ::result/weight 1})
  (core/match (set-equals 1) [1 3])
  => (just {::result/type   :mismatch
            ::result/value  (just {:expected-type-msg #"^set-equals*"
                                   :provided          #"^provided: 1"})
            ::result/weight 1}))

(fact "embeds /set-equals mismatches due to content"
  (core/match (set-embeds pred-set) #{1 -2})
  => (just {::result/type  :mismatch
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})
            ::result/weight 1})

  (core/match (set-embeds pred-seq) #{1 -2})
  => (just {::result/type :mismatch
            ::result/weight 1
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})})

  (core/match (equals pred-set) #{1 -2})
  => (just {::result/type :mismatch
            ::result/weight 1
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})})

  (core/match (set-equals pred-seq) #{1 -2})
  => (just {::result/type :mismatch
            ::result/weight 1
            ::result/value (just #{1 (just {:actual   -2
                                            :expected anything})})}))

(def even-odd-set #{(pred-matcher #(and (odd? %) (pos? %)))
                    (pred-matcher even?)})
(def even-odd-seq (into [] even-odd-set))
(fact "Order agnostic checks show fine-grained mismatch details"
  (core/match (equals even-odd-set) #{1 2 -3})
  => (just {::result/type   :mismatch
            ::result/value  #{1 2 (model/->Unexpected -3)}
            ::result/weight 1})

  (core/match (in-any-order even-odd-seq) [1 2 -3])
  => (just {::result/type   :mismatch
            ::result/value  (just [1 2 (model/->Unexpected -3)]
                                  :in-any-order)
            ::result/weight 1})

  (core/match (in-any-order even-odd-seq) [1])
  => (just {::result/type   :mismatch
            ::result/value  (just [1 (just (model/->Missing anything))]
                                  :in-any-order)
            ::result/weight 1})

  (core/match (equals even-odd-set) #{1})
  => (just {::result/type   :mismatch
            ::result/value  (just #{1 (just (model/->Missing anything))}
                                  :in-any-order)
            ::result/weight 1}))

(fact "in-any-order minimal mismatch test"
  (core/match (equals [(equals {:a (equals "1") :x (equals "12")})])
    [{:a "1" :x "12="}])
  => {::result/type   :mismatch
      ::result/value  [{:a "1" :x (model/->Mismatch "12" "12=")}]
      ::result/weight 1}

  (core/match (in-any-order [(equals {:a (equals "2") :x (equals "14")})
                             (equals {:a (equals "1") :x (equals "12")})])
    [{:a "1" :x "12="} {:a "2" :x "14="}])
  => (just {::result/type   :mismatch
            ::result/value  (just [{:a "1" :x (model/->Mismatch "12" "12=")}
                                   {:a "2" :x (model/->Mismatch "14" "14=")}]
                                  :in-any-order)
            ::result/weight 2})

  (core/match (in-any-order [(equals {:a (equals "2") :x (equals "14")})
                             (equals {:a (equals "1") :x (equals "12")})])
    [{:a "1" :x "12="} {:a "2" :x "14="}])
  => (just {::result/type   :mismatch
            ::result/value  (just [{:a "2" :x (model/->Mismatch "14" "14=")}
                                   {:a "1" :x (model/->Mismatch "12" "12=")}]
                                  :in-any-order)
            ::result/weight 2}))

(deftest matcher-for-special-cases
  (testing "matcher for a fn is a fn"
    (is (= (class (matchers/pred (fn [])))
           (class (core/matcher-for (fn []))))))
  (testing "matcher for a map is embeds"
    (is (= (class (matchers/embeds {}))
           (class (core/matcher-for {})))))
  (testing "matcher for a regex"
    (is (= (class (matchers/regex #"abc"))
           (class (core/matcher-for #"abc"))))))

(defspec matcher-for-most-cases
  {:doc "matchers/equals is the default matcher for everything but functions, regexen, and maps."
   :num-tests 1000
   :max-size  10}
  (prop/for-all [v (gen/such-that
                    (fn [v] (and (not (map? v))
                                 (not (instance? java.util.regex.Pattern v))
                                 (not (fn? v))))
                    gen/any)]
                (= (class (matchers/equals v))
                   (class (core/matcher-for v)))))

(defn greater-than-matcher [expected-long]
  (core/->PredMatcher
   (fn [actual] (> actual expected-long))
   (str "greater than " expected-long)))

(deftest matcher-for-works-within-match-with
  (is (match-with? {java.lang.Long greater-than-matcher}
                   (core/matcher-for 4)
                   5)))

(spec.test/unstrument)
