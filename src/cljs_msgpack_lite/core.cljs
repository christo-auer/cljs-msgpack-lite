(ns cljs-msgpack-lite.core
  (:require [goog.object :as gobject]
            [cljs.spec.alpha :as s]
            [camel-snake-kebab.core :refer [->camelCase]]))


(def msgpack 
  ^:private
  (js/require "msgpack-lite"))

(def Buffer 
  ^:private
  (js/require "msgpack-lite/lib/buffer-global"))

(def Codec
  ^:private
  (->  "msgpack-lite/lib/codec-base" js/require .-preset type))

; specs
(s/def ::any any?)
(s/def ::buffer #(= (type %) Buffer))
(s/def ::byte-array (s/coll-of (s/and int? #(<= 0 % 255))))
(s/def ::codec #(= (type %) Codec))
(s/def ::encode-options (s/keys :opt-un [::codec]))

(defn ->buffer 
  "Convert an array of bytes into a buffer, leaves a buffer as a buffer.
  Takes an array of bytes and turns into in to a msgpack-lite buffer. If `v`
  is already a buffer, `v` is left alone. Note that `clj->js` is applied to
  `v` before it is turned into a buffer.
  Example:
    (->buffer [0x12 0x13 0x14])"
  {:pre (s/assert (s/or ::buffer ::buffer ::byte-array ::byte-array))
   :post (s/assert ::buffer?)}
  [v]
  (Buffer (clj->js v)))


(defn- ->camel-case-keys 
  [options]
  (into
    {}
    (for [[k v] options
          :let [k (-> k (->camelCase keyword))]]
      [k v])))

(def 
  ^:private 
  prepare-options (comp clj->js ->camel-case-keys))

(defn 
  ^:dynamic
  encode [value & {:as options}]
  "Encodes the given value using `msgpack-lite`'s encode.
  The encoded value is returned in a `buffer`.
  Any options are passed to `msgpack-lite`'s `encode` in a js'ed form, e.g.,
  `encode value :foo-bar 42` results in `encode value {fooBar: 42}`.
  An optional `codec` can be passed with `:codec codec`.
  Note: `encode` is rebound so that any options passed to
  the initial call are automatically passed to any subsequent call of `encode`.
  Any options passed to recursive calls of `encode` 
  overrides prior options (as in `merge`)."
  {:pre [(s/assert ::any value)
         (s/assert ::encode-options options)]
   :post [#(s/assert ::buffer %)]}
  (letfn [(-encode [value & {:as more-options}]
            (let [options (merge options more-options)
                  codec (:codec options)
                  value (if (and codec (.getExtPacker codec value))
                          value
                          (clj->js value))]
            (msgpack.encode 
              value 
              (prepare-options options))))]
    (binding [encode -encode]
      (encode value))))


(defn 
  ^:dynamic
  decode [buffer & {:as options}]
  "Decodes the given buffer using `msgpack-lite`'s decode.
  `buffer` can be a `buffer` or a byte array (e.g., `[0x03 0x3f]`).
  Any options are passed to `msgpack-lite`'s `decode` in a js'ed form, e.g.,
  `decode value :foo-bar 42` results in `decode value {fooBar: 42}`.
  An optional `codec` can be passed with `:codec codec`.
  Note: `decode` is rebound so that any options passed to
  the initial call are automatically passed to any subsequent call of `decode`.
  Any options passed to recursive calls of `decode` 
  overrides prior options (as in `merge`)."
  (letfn [(-decode [buffer & {:as more-options}]
            (let [options (merge options more-options)
                  {keywordize-keys :keywordize-keys 
                   js->clj :js->clj
                   :as options :or {keywordize-keys false js->clj true}} options
                  js-options (-> options
                                 (dissoc :keywordize-keys :js->clj)
                                 prepare-options)
                  buffer (->buffer buffer)
                  post-proc (case [js->clj keywordize-keys]
                              [false false] identity
                              [false true]  clojure.walk/keywordize-keys
                              #(cljs.core/js->clj % :keywordize-keys keywordize-keys))]
              (-> buffer
                  (msgpack.decode js-options)
                  post-proc)))]
    (binding [decode -decode]
      (decode buffer))))


(defn create-codec [& {:as options}] 
  (msgpack.createCodec (prepare-options options)))

(defn add-ext-packer! [codec id type packer]
  (.addExtPacker codec id type packer)
  codec)

(defn add-ext-unpacker! [codec id unpacker]
  (.addExtUnpacker codec id unpacker)
  codec)

(defn add-ext-packers! [codec id type packer unpacker]
  (-> codec
      (add-ext-packer! id type packer)
      (add-ext-unpacker! id unpacker)))


(defn create-encode-stream [& {:as options}]
  (let [options (prepare-options options)]
    (msgpack.createWriteStream options)))

(defn create-decode-stream [& {:as options}]
  (let [options (prepare-options options)]
    (msgpack.createDecodeStream options)))
