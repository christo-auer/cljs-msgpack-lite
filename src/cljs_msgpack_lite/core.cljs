(ns cljs-msgpack-lite.core
  (:require [cljs.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [goog.object :as gobject]
            [camel-snake-kebab.core :refer [->camelCase]]))

(def node-stream (js/require "stream"))
(def Transform (.-Transform node-stream))

(def msgpack 
  ^:private
  (js/require "msgpack-lite"))

(def Buffer 
  ^:private
  (js/require "msgpack-lite/lib/buffer-global"))

(def Codec
  ^:private
  (->  "msgpack-lite/lib/codec-base" js/require .-preset type))

(def Decoder (.-Decoder msgpack))

(def Encoder (.-Encoder msgpack))

; specs
(defn- bool? [x] (contains? #{true false} x))
(s/def ::any any?)
(s/def ::Buffer #(instance? Buffer %))
(s/def ::Transform (partial instance? (.-Transform node-stream)))
(s/def ::byte (s/and int? #(<= 0 % 255)))
(s/def ::byte-array (s/coll-of ::byte :kind vec))
(s/def ::codec #(instance? Codec %))
(s/def ::keywordize-keys (partial contains? #{true false}))
(s/def ::js->clj (partial contains? #{true false}))
(s/def ::preset bool?)
(s/def ::safe bool?)
(s/def ::useraw bool?)
(s/def ::binarraybuffer bool?)
(s/def ::uint8array bool?)
(s/def ::usemap bool?)
(s/def ::codec #(= (type %) Codec))
(s/def ::packer fn?)
(s/def ::unpacker fn?)
(s/def ::type fn?)

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

(defn- codec-clj->js
  [codec x]
  (cond
    (and (some? x) codec (.getExtPacker codec x)) x
    (nil? x) nil
    (keyword? x) (name x)
    (symbol? x) (str x)
    (map? x) (let [m (js-obj)]
               (doseq [[k v] x]
                 (gobject/set m (key->js k) (codec-clj->js codec v)))
               m)
    (coll? x) (let [arr (array)]
                (doseq [x (map (partial codec-clj->js codec) x)]
                  (.push arr x))
                arr)
    :else x))

(defn- mk-internal-encode [options]
 (fn [value & {:as more-options}]
                  (let [options (merge options more-options)
                        codec (:codec options)
                        value (codec-clj->js codec value)]
                    (msgpack.encode 
                      value 
                      (prepare-options options)))))

(defn 
  ^:dynamic
  encode 
  "Encodes the given value using `msgpack-lite`'s encode.
  The encoded value is returned in a `buffer`.
  An optional `codec` can be passed with `:codec codec`.
  Note: `encode` is rebound so that any options passed to
  the initial call are automatically passed to any subsequent 
  call of `encode`.  Any options passed to recursive calls of 
  `encode` overrides prior options (as in `merge`)."
  [value & {:as options}]
  (binding [encode (mk-internal-encode options)]
    (encode value)))

(s/fdef
  encode
  :args (s/cat :value ::any :options (s/keys* :opt-un [::codec] )) 
  :ret ::Buffer)

(defn- mk-decode-post-proc [options]
  (let [{keywordize-keys :keywordize-keys 
         js->clj :js->clj
         :or {keywordize-keys false js->clj true}} options]
    (case [js->clj keywordize-keys]
      [false false] identity
      [false true]  clojure.walk/keywordize-keys
      #(cljs.core/js->clj % :keywordize-keys keywordize-keys))))

(defn- mk-internal-decode [options]
  (fn [buffer & {:as more-options}]
    (let [options (merge options more-options)
          post-proc (mk-decode-post-proc options)
          js-options (-> options
                         (dissoc :keywordize-keys :js->clj)
                         prepare-options)]
      (-> buffer
          (msgpack.decode js-options)
          post-proc))))

(defn 
  ^:dynamic
  decode 
  "Decodes the given buffer using `msgpack-lite`'s decode.
  `buffer` can be a `buffer` or a byte array (e.g., `[0x03 0x3f]`).
  An optional `codec` can be passed with `:codec codec`.
  Any object decoded is passed to `js->clj` unless `:js->clj false` is 
  passed as option (default is `:js->clj true`. If `:keywordize-keys true`
  is passed as option, `clojure.walk/keywordize-keys` is applied to
  the decoded object, i.e., all keys of maps are replaced by keywords.
  Default is `:keywordize-keys false`.
  Note: `decode` is rebound so that any options passed to
  the initial call are automatically passed to any subsequent call of `decode`.
  Any options passed to recursive calls of `decode` 
  overrides prior options (as in `merge`)."
  [buffer & {:as options}]
  (binding [decode (mk-internal-decode options)]
    (decode buffer)))

(s/fdef
  decode
  :args (s/cat :buffer (s/or ::Buffer ::Buffer ::byte-array ::byte-array) :opts (s/keys* :opt-un [::codec ::keywordize-keys ::js->clj]))
  :post ::any)

(defn create-codec 
  "Creates and returns a new codec with the given options. 
  The options are defined on https://github.com/kawanet/msgpack-lite 
  and can be defined by keywords, e.g., `(create-codec :useraw true)`" 
  [& {:as options}]
  (msgpack.createCodec (prepare-options options)))

(s/fdef create-codec 
        :args (s/cat :create-codec-options (s/keys* :opt-un [::preset ::safe ::useraw ::binarraybuffer ::uint8array ::usemap])) 
        :post ::codec)

(defn add-ext-packer! 
  "Adds the given packer to the codec, where `id` is the identifier for the 
  msgpack extension (`(<= 0 id 255)`), type is the data type for which 
  to use the packer and `packer` is a function that takes a value of 
  type `type` and returns a `buffer` in which the value is encoded.
  Returns the modified codec."
  [codec id type packer]
  (.addExtPacker codec id type packer)
  codec)

(s/fdef 
  add-ext-packer!
  :args (s/cat :codec ::codec :id ::byte :type ::type :packer ::packer)
  :ret ::codec)


(defn add-ext-unpacker! 
  "Adds the given unpacker to the codec, where `id` is the identifier for the 
  msgpack extension (`(<= 0 id 255)`) and `unpacker` is a function that takes 
  a buffer, and extracts and returns the corresponding value.
  Returns the modified codec."
  [codec id unpacker]
  (let [unpacker 
        (fn [buffer]
          (let [decoded (unpacker buffer)]
            (reify
              IEncodeClojure
              (-js->clj [_ _] decoded))))]
    (.addExtUnpacker codec id unpacker))
  codec)

(s/fdef 
  add-ext-unpacker!
  :args (s/cat :codec ::codec :id ::byte :unpacker ::unpacker)
  :ret ::codec)

(defn add-ext-packers! 
  "Adds the given pakcer and unpacker to the codec. See `add-ext-packer!` 
  and `add-ext-unpacker!` for more information."
  [codec id type packer unpacker]
  (-> codec
      (add-ext-packer! id type packer)
      (add-ext-unpacker! id unpacker)))

(s/fdef 
  add-ext-packers!
  :args (s/cat :codec ::codec :id ::byte :type ::type :packer ::packer :unpacker ::unpacker)
  :ret ::codec)

(defn create-encode-transform 
  "Creates an encode transform that can be piped to another
  stream. With `options` an optional codec can be defined with 
  `:codec codec`. As with `encode`, the symbol
  `encode` is rebound so that any options passed to
  `create-encode-transform` are automatically passed to any subsequent 
  call of `encode`, in particular `:codec codec`.  Any options 
  passed to recursive calls of `encode` overrides prior 
  options (as in `merge`)."
  [& {:as options}]
  (let [internal-encode (mk-internal-encode options)
        encoder-buffer (Encoder (prepare-options options))
        encoder-transform (fn [chunk encoding callback]
                            (binding [encode internal-encode]
                              (this-as this
                                       (.write encoder-buffer (codec-clj->js (:codec options) chunk))
                                       (.push this (.read encoder-buffer))
                                       (if (some? callback) (callback)))))
        encoder-flush (fn [callback]
                        (.flush encoder-buffer)
                        (if (some? callback) (callback)))
        transform (Transform. (clj->js {:objectMode true :transform encoder-transform :flush encoder-flush}))]
    (set! (.-push encoder-buffer) (fn [chunk] (.push transform chunk)))
    transform))

(s/fdef
  create-encode-transform
  :args (s/cat :options (s/keys* :opt-un [::codec]))
  :post ::Transform)

(defn create-decode-transform 
  "Creates a decode transform that can read from another stream (via `.pipe`).
  An optional `codec` can be passed with `:codec codec`.
  Any object decoded is passed to `js->clj` unless `:js->clj false` is 
  passed as option (default is `:js->clj true`. If `:keywordize-keys true`
  is passed as option, `clojure.walk/keywordize-keys` is applied to
  the decoded object, i.e., all keys of maps are replaced by keywords.
  Default is `:keywordize-keys false`.
  Note: `decode` is rebound so that any options passed to
  `create-decode-transform` are automatically passed to any subsequent 
  call of `decode`.  Any options passed to recursive calls of `decode` 
  overrides prior options (as in `merge`)."
  [& {:as options}]
  (let [internal-decode (mk-internal-decode options)
        decoder-buffer (Decoder (prepare-options options))
        post-proc (mk-decode-post-proc options)
        decoder-transform
        (fn [chunk encoding callback]
          (binding [decode internal-decode]
            (this-as this
                     (.write decoder-buffer chunk)
                     (.flush decoder-buffer)
                     (if (some? callback) (callback)))))
        transform (Transform. (clj->js {:readableObjectMode true :transform decoder-transform}))]
    (set! (.-push decoder-buffer) (fn [chunk] (.push transform (post-proc chunk))))
    transform))

(s/fdef
  create-decode-transform
  :args (s/cat :options (s/keys* :opt-un [::codec ::keywordize-keys ::js->clj]))
  :post ::Transform)

(st/instrument `cljs-msgpack-lite.core)

