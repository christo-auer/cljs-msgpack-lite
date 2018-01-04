(ns cljs-msgpack-lite.clj-codec
  (:require [cljs.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [cljs-msgpack-lite.core :refer [encode decode create-codec add-ext-packers! codec-clj->js create-encode-transform create-decode-transform]]))

(defrecord CljChar [character])

(s/def ::character (s/and string? #(= 1 (count %))))
(s/fdef
  ->CljChar
  :args (s/cat :character ::character))

(defrecord CljRatio [numerator denominator])

(s/fdef
  ->CljRatio
  :args (s/cat :numerator integer? :denominator integer?))

(defn- keyword-packer [keyword]
  (-> keyword
      str
      (subs 1)
      encode))

(defn- keyword-unpacker [buffer]
  (-> buffer
      decode
      keyword))

(defn- symbol-packer [symbol]
  (-> symbol
      str
      encode))

(defn- symbol-unpacker [buffer]
  (-> buffer
      decode
      symbol))

(defn- char-packer [character]
  (-> character .-character encode))

(defn- char-unpacker [buffer]
  (-> buffer
      decode
      ->CljChar))

(defn- ratio-packer [ratio]
  (let [array [(.-numerator ratio) (.-denominator ratio)]]
    (encode array)))

(defn- ratio-unpacker [buffer]
  (let [[numerator denominator] (decode buffer)]
    (->CljRatio numerator denominator)))

(defn- hash-set-packer [hash-set]
  (-> hash-set
      vec
      encode))

(defn- hash-set-unpacker [buffer]
  (-> buffer
      decode
      set))

(defn- hash-map-packer [hm]
  (-> hm
      vec
      encode))

(defn- hash-map-unpacker [buffer]
  (->> buffer
      decode
      (into {})))

(defn- list-packer [l]
  (-> l
      vec
      encode))

(defn- list-unpacker [buffer]
  (->> buffer
       decode
       (apply list)))

(defn- empty-list-packer [l]
  (encode []))

(defn- empty-list-unpacker [buffer]
  '())

(defn create-clj-codec 
  "Creates and returns a codec for encoding and decoding Clojure(Script)-specific types:
  `Keyword` (`0x03`), `Symbol` (`0x04`), `PersistentHashset` (`0x07`). As ClojureScript
  does not have `Char` and `Ratio`, surrogate types `CljChar` and `CljRatio` are defined 
  (`0x04` and `0x05`, respectively). These extensions are compatible with 
  https://github.com/edma2/clojure-msgpack .


  If `:include-map true` is passed, `PersistentArrayMap` can also be directly encoded
  and decoded with id `0x08` (default is `:include-map true`).
  If `:include-list true` is passed, an extension for the empty list `'()` (`0x09`) and
  for non-empty lists (`0x10`) are added (default is `:include-list false`).

  Note that the latter two extensions are NOT compatible with 
  https://github.com/edma2/clojure-msgpack ."
  [& {:keys [include-map include-list] :or {:include-map false :include-list false}}]
  (cond-> 
    (create-codec)
    true (add-ext-packers! 0x03 Keyword keyword-packer keyword-unpacker)
    true (add-ext-packers! 0x04 Symbol symbol-packer symbol-unpacker)
    true (add-ext-packers! 0x05 CljChar char-packer char-unpacker)
    true (add-ext-packers! 0x06 CljRatio ratio-packer ratio-unpacker)
    true (add-ext-packers! 0x07 PersistentHashSet hash-set-packer hash-set-unpacker)
    ; extension to CLJ types
    include-map (add-ext-packers! 0x08 PersistentArrayMap hash-map-packer hash-map-unpacker)
    ; '() and '(*non-empty*) are different types, so we have to define two extensions
    include-list     (add-ext-packers! 0x09 EmptyList empty-list-packer empty-list-unpacker)
    include-list     (add-ext-packers! 0x10 List list-packer list-unpacker)))

(s/def ::include-map (partial contains? #{true false}))
(s/def ::include-list (partial contains? #{true false}))
(s/fdef create-clj-codec
  :args (s/cat :opts (s/keys* :opt-un [::include-map ::include-list])))


(st/instrument `cljs-msgpack-lite.clj-codec)
