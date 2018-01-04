(ns cljs-msgpack-lite.clj-codec-test
  (:require [cljs.test :refer-macros [deftest are is testing]]
            [cljs-msgpack-lite.core-test :refer [test-encode-decode]]
            [cljs-msgpack-lite.clj-codec :refer [->CljChar ->CljRatio create-clj-codec]]))

(deftest clj-codec-arg-test
  (testing "arg checking"
    (is (thrown? js/Error (->CljChar nil)))
    (is (thrown? js/Error (->CljChar "")))
    (is (thrown? js/Error (->CljChar "ab")))
    (is (thrown? js/Error (->CljRatio nil nil)))
    (is (thrown? js/Error (->CljRatio 3.1415 1)))
    (is (thrown? js/Error (->CljRatio 1 "2")))
    (is (thrown? js/Error (create-clj-codec :include-list "yes")))
    (is (thrown? js/Error (create-clj-codec :include-list)))
    (is (thrown? js/Error (create-clj-codec :include-map :you-bet)))))

(deftest clj-codec-test
  (let [codec (create-clj-codec :include-list true :include-map true)]
    (testing "clj scalar values"
      (test-encode-decode [(keyword "") :foo :foo/bar ::foo
                           (symbol "") 'foo 'foo/bar
                           (->CljChar "a")
                           (->CljRatio 1 2) (->CljRatio 1 0)
                           #{} #{"foo" "bar"}
                           '() '("foo" "bar")
                           {} {"foo" "bar"} ] :codec codec))
    (testing "clj nested values"
      (test-encode-decode [#{:foo :bar (->CljChar "a") {:foo (->CljRatio 1 2)}}
                           '(({}))
                           [#{{:foo '(list-element-1 list-element-2 qualified/list-element)}}]
                           #{#{{(->CljRatio 1 2) 'a-symbol :foo/bar (->CljChar "a")}}}
                           ] :codec codec))))
