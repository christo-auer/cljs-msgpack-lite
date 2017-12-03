(ns cljs-msgpack-lite.clj-codec
  (:require [cljs-msgpack-lite.core :refer [encode decode create-codec add-ext-packers!]]))

(deftype CljChar [character])

(deftype CljRatio [numerator denominator])

(defn- keyword-packer [keyword]
  (-> keyword
      name
      encode))

(defn- keyword-unpacker [buffer]
  (-> buffer
      decode
      keyword))

(defn- symbol-packer [symbol]
  (-> symbol
      name
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
      seq
      encode))

(defn- hash-set-unpacker [buffer]
  (-> buffer
      decode
      set))


(defn create-clj-codec []
  (-> 
    (create-codec :preset true)
    (add-ext-packers! 0x03 Keyword keyword-packer keyword-unpacker)
    (add-ext-packers! 0x04 Symbol symbol-packer symbol-unpacker)
    (add-ext-packers! 0x05 CljChar char-packer char-unpacker)
    (add-ext-packers! 0x06 CljRatio ratio-packer ratio-unpacker)
    (add-ext-packers! 0x07 PersistentHashSet hash-set-packer hash-set-unpacker)))
