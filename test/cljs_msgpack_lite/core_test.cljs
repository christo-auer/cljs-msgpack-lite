(ns cljs-msgpack-lite.core-test
  (:require [cljs.core.async :refer [>! <! chan] :refer-macros [go go-loop]]
            [cljs.test :refer-macros [deftest are is testing async]]
            [cljs-msgpack-lite.core :as c]))

(defn test-encode-decode [values & opts]
  (doseq [value values]
    (is (= value (as-> value _ (apply c/encode _ opts) (apply c/decode _ opts))))))

(deftest encode-decode
  (testing "encode/decode parameter checking"
    (is (thrown? js/Error (c/encode nil :codec  "I'm a codec, I swear!")))
    (is (thrown? js/Error (c/decode nil)))
    (is (thrown? js/Error (c/decode [0x01 0x02])))
    (is (thrown? js/Error (c/decode (c/encode nil) :codec "I'm a codec, I swear!"))))
  (testing "encoding/decoding scalar values"
    (test-encode-decode [nil "a string" 1 3.1415 false true]))
  (testing "encoding/decoding arrays"
    (test-encode-decode [[] [nil] [1 2 3] [[]] [[] [[]]]]))
  (testing "encoding/decoding maps"
    (test-encode-decode [{} {:foo "foo"} {:bar nil} {:bar {:foo "foo"}}] :keywordize-keys true))
  (testing "encoding/decoding nested structures"
    (test-encode-decode [[{:foo "bar"} {:bar [{} {:baz 123}]}] {:foo {:bar ["baz" 1234]}}] :keywordize-keys true)))

(defrecord TestRecord [x y z])

(defn test-record-packer [{:keys [x y z]}]
  (c/encode [x y z]))

(defn test-record-unpacker [buffer]
  (->> buffer c/decode (apply ->TestRecord) ))

(deftest extension-test
  (testing "codec: arg checking"
    (let [codec (c/create-codec)]
      (is (thrown? js/Error (c/add-ext-packer! nil 0x10 TestRecord test-record-packer)))
      (is (thrown? js/Error (c/add-ext-packer! codec -1 TestRecord test-record-packer)))
      (is (thrown? js/Error (c/add-ext-packer! codec 0x10 :foo test-record-packer)))
      (is (thrown? js/Error (c/add-ext-packer! codec 0x10 TestRecord "I'm a packer, I swear!")))
      (is (thrown? js/Error (c/add-ext-unpacker! nil 0x10 test-record-unpacker)))
      (is (thrown? js/Error (c/add-ext-unpacker! codec -1 test-record-unpacker)))
      (is (thrown? js/Error (c/add-ext-unpacker! codec 0x10 "I'm an unpacker, I swear!")))
      (is (thrown? js/Error (c/add-ext-packers! nil 0x10 TestRecord test-record-packer test-record-unpacker)))
      (is (thrown? js/Error (c/add-ext-packers! codec -1 TestRecord test-record-packer test-record-unpacker)))
      (is (thrown? js/Error (c/add-ext-packers! codec 0x10 :foo test-record-packer test-record-unpacker)))
      (is (thrown? js/Error (c/add-ext-packers! codec 0x10 TestRecord "I'm a packer, I swear!" test-record-unpacker)))
      (is (thrown? js/Error (c/add-ext-packers! codec 0x10 TestRecord test-record-packer "I'm a unpacker, I swear!" )))))
  (testing "codec: encoding/decoding"
    (let [codec (-> (c/create-codec)
                    (c/add-ext-packers! 0x10 TestRecord test-record-packer test-record-unpacker))
          values (map #(apply ->TestRecord %) [[nil nil nil] 
                                               [1 2 3] 
                                               [(->TestRecord 1 2 3) 
                                                (->TestRecord 
                                                  (->TestRecord 1 2 3) 
                                                  (->TestRecord 1 2 3) 
                                                  (->TestRecord 1 2 3)) 
                                                (->TestRecord "foo" "bar" "baz")]])]
      (test-encode-decode values :codec codec))))


(deftest transform-test
  (async done
         (let [codec (-> (c/create-codec)
                         (c/add-ext-packers! 0x10 TestRecord test-record-packer test-record-unpacker))
               decoder (c/create-decode-transform :codec codec :keywordize-keys true)
               encoder (c/create-encode-transform :codec codec :keywordize-keys true)
               ch (chan)
               test-values (into  [[nil] [] {}
                                   [{:foo "bar"} {:bar [{} {:baz 123}]}] {:foo {:bar ["baz" 1234]}}]
                                 (map #(apply ->TestRecord %) [[nil nil nil] 
                                                               [1 2 3] 
                                                               [(->TestRecord 1 2 3) 
                                                                (->TestRecord 
                                                                  (->TestRecord 1 2 3) 
                                                                  (->TestRecord 1 2 3) 
                                                                  (->TestRecord 1 2 3)) 
                                                                (->TestRecord "foo" "bar" "baz")]]
                                      (repeat 8192 1)))]
           (.pipe encoder decoder)
           (.on decoder "data" #(go (>! ch %)))
           (go-loop [[value & tail] test-values]
                    (is (.write encoder value))
                    (is (= value (<! ch)))
                    (if (seq tail) 
                      (recur tail)
                      (do
                        (.end encoder)
                        (done)))))))

