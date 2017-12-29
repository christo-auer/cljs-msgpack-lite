(ns cljs-msgpack-lite.core
  (:require [cljs.spec.alpha :as s]
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
(defn- bool? [x] (contains? #{true false} x))
(s/def ::any any?)
(s/def ::buffer #(= (type %) Buffer))
(s/def ::byte (s/and int? #(<= 0 % 255)))
(s/def ::byte-array (s/coll-of ::byte))
(s/def ::codec #(= (type %) Codec))
(s/def ::encode-options (s/keys :opt-un [::codec]))
(s/def ::decode-options (s/keys :opt-un [::codec]))
(s/def ::preset bool?)
(s/def ::safe bool?)
(s/def ::useraw bool?)
(s/def ::binarraybuffer bool?)
(s/def ::uint8array bool?)
(s/def ::usemap bool?)
(s/def ::create-codec-options (s/keys :opt-un [::preset ::safe ::useraw ::binarraybuffer ::uint8array ::usemap]))
(s/def ::codec #(= (type %) Codec))
(s/def ::packer fn?)
(s/def ::unpacker fn?)
(s/def ::type fn?)

(defn ->buffer 
  "Convert an array of bytes into a buffer, leaves a buffer as a buffer.
  Takes an array of bytes and turns into in to a msgpack-lite buffer. If `v`
  is already a buffer, `v` is left alone. Note that `clj->js` is applied to
  `v` before it is turned into a buffer.
  Example:
    (->buffer [0x12 0x13 0x14])"
  {:pre (s/assert (s/or ::buffer ::buffer ::byte-array ::byte-array))
   :post (s/assert ::buffer)}
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
  An optional `codec` can be passed with `:codec codec`.
  Note: `encode` is rebound so that any options passed to
  the initial call are automatically passed to any subsequent 
  call of `encode`.  Any options passed to recursive calls of 
  `encode` overrides prior options (as in `merge`)."
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
  An optional `codec` can be passed with `:codec codec`.
  Note: `decode` is rebound so that any options passed to
  the initial call are automatically passed to any subsequent call of `decode`.
  Any options passed to recursive calls of `decode` 
  overrides prior options (as in `merge`)."
  {:pre [(s/assert (s/or ::buffer ::buffer ::byte-array ::byte-array) buffer)
         (s/assert ::decode-options options)]
   :post [#(s/assert ::any %)]}
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
  "Creates and returns a new codec with the given options. 
  The options are defined on https://github.com/kawanet/msgpack-lite 
  and can be defined by keywords, e.g., `(create-codec :useraw true)`"
  {:pre [(s/assert ::create-codec-options options)]
   :post [#(s/assert ::codec %)]}
  (msgpack.createCodec (prepare-options options)))

(defn add-ext-packer! [codec id type packer]
  "Adds the given packer to the codec, where `id` is the identifier for the 
  msgpack extension (`(<= 0 id 255)`), type is the data type for which 
  to use the packer and `packer` is a function that takes a value of 
  type `type` and returns a `buffer` in which the value is encoded.
  Returns the modified codec."
  {:pre [(s/assert ::codec codec)
         (s/assert ::byte id)
         (s/assert ::type type)
         (s/assert ::packer packer)]
   :post [#(s/assert ::codec %)]}
  (.addExtPacker codec id type packer)
  codec)

(defn add-ext-unpacker! [codec id unpacker]
  "Adds the given unpacker to the codec, where `id` is the identifier for the 
  msgpack extension (`(<= 0 id 255)`) and `unpacker` is a function that takes 
  a buffer, and extracts and returns the corresponding value.
  Returns the modified codec."
  {:pre [(s/assert ::codec codec)
         (s/assert ::byte id)
         (s/assert ::packer unpacker)]
   :post [#(s/assert ::codec %)]}
  (let [unpacker 
        (fn [buffer]
          (let [decoded (unpacker buffer)]
            (reify
              IEncodeClojure
              (-js->clj [_ _] decoded))))]
    (.addExtUnpacker codec id unpacker))
  codec)

(defn add-ext-packers! [codec id type packer unpacker]
  "Adds the given pakcer and unpacker to the codec. See `add-ext-packer!` 
  and `add-ext-unpacker!` for more information."
  {:pre [(s/assert ::codec codec)
         (s/assert ::byte id)
         (s/assert ::type type)
         (s/assert ::packer packer)
         (s/assert ::unpacker unpacker)]
   :post [#(s/assert ::codec %)]}
  (-> codec
      (add-ext-packer! id type packer)
      (add-ext-unpacker! id unpacker)))
