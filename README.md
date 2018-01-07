[![Clojars Project](https://img.shields.io/clojars/v/cljs-msgpack-lite.svg)](https://clojars.org/cljs-msgpack-lite)

# Introduction

`cljs-msgpack-lite` is a lightweight and convenient wrapper around
[msgpack-lite](https://github.com/kawanet/msgpack-lite) for ClojureScript.

# Usage and Examples

## Dependencies

Add
```clojure
[cljs-msgpack-lite "0.1.5"]
```
to your project dependencies (e.g., `project.clj` for leiningen).

Also, install the `msgpack-lite` node package, e.g.:
```sh
npm install msgpack-lite --save
```

## Encode and Decode

`cljs-msgpack-lite` defines `encode` to encode objects into a `buffer`:
```clojure
=> (def buffer (encode 42))
#'cljs-msgpack-lite.core/buffer
=> (type buffer)
#object[Buffer]
```
`decode` decodes a buffer:
```clojure
=> (decode buffer)
42
```
Collections (`coll?`), maps (`map?`) and sets (`PersistentHashSet`) are
automatically converted into their JS counterparts before encoding and after
decoding:
```clojure
=> (-> ["foo" "bar" "baz"] encode decode)
["foo" "bar" "baz"]
=> (-> {:foo "foo" :bar "bar"} encode decode)
{"foo" "foo", "bar" "bar"}
```
Note that the keys are turned into strings, which already happens during
`encode` (JS does only support strings for keys). To keywordize keys
recursively, add `:keywordize-keys true` to decode:
```clojure
=> (-> {:foo "foo" :bar "bar"} encode (decode :keywordize-keys true))
{:foo "foo", :bar "bar"}
```
`decode` internally uses `js->clj` to convert any JS objects to their
ClojureScript counterparts. If you don't want this, add `:js->clj false` to
`decode` to get the "raw" result:
```clojure
=> (-> {:foo "foo" :bar "bar"} encode (decode :js->clj false))
#js {:foo "foo", :bar "bar"}
```

## Clojure(Script) Types

For encoding and decoding ClojureScript types unknown to JS (e.g., keywords),
the codec created by `cljs-msgpack-lite.clj-codec/create-clj-codec` can be used.
A codec is passed to `encode` and `decode` via the `:codec` option:
```clojure
=> (require '[cljs-msgpack-lite.clj-codec :refer [create-clj-codec]])
nil
=> (def clj-codec (create-clj-codec))
=> (-> :foo (encode :codec clj-codec) (decode :codec clj-codec))
:foo
```
`clj-codec` defines the same extensions as defined by
[clojure-msgpack](https://github.com/edma2/clojure-msgpack) with the same type
identifiers so that both are mutually compatible. However, ClojureScript does
not support Clojure's `Ratio` and `Char`. For these two types, surrogate types
are defined in `cljs-msgpack-lite`:
```clojure
(defrecord CljChar [character])
(defrecord CljRatio [numerator denominator])
```
For example:
```clojure
=> (def ratio-buffer (-> (->CljRatio 1 3) (encode :codec clj-codec)))
#'cljs-msgpack-lite.core/ratio-buffer

=> (def ratio (decode ratio-buffer :codec clj-codec))
#'cljs-msgpack-lite.core/ratio

=> (:numerator ratio)
1
=> (:denominator ratio)
3
```
The following table summarizes all types in accordancw with
[clojure-msgpack](https://github.com/edma2/clojure-msgpack):

ClojureScript Type                     | Message Pack ID
---------------------------------------|----------------
`Keyword`                              | 0x03
`Symbol`                               | 0x04
`cljs-msgpack-lite.clj-codec/CljChar`  | 0x05
`cljs-msgpack-lite.clj-codec/CljRatio` | 0x06
`PersistentHashSet`                    | 0x07

By default, CLJS maps are converted to and from JS objects. In particular, the
keys of CLJS maps are converted to strings as JavaScript only supports this type
of key. Also, CLJS lists are converted to JS arrays, losing their flavour of
being a list. To avoid this, additional extensions are defined for CLJS maps
(`PersistentArrayMap`) and CLJS lists, for technical reasons one for
the empty list `'()` and for non-empty lists.

ClojureScript Type                     | Message Pack ID
---------------------------------------|----------------
`PersistentArrayMap`                   | 0x08
`EmptyList`                            | 0x09
`List`                                 | 0x10

These can be inluded by passing `:include-map true` for maps and `:include-list
true` for lists to `create-clj-codec`, e.g., `(create-clj-codec :include-map
true :include-list true)`. 

By default, these extensions are deactivated and they
**break compatibility** with [clojure-msgpack](https://github.com/edma2/clojure-msgpack).

## Defining a Codec

You can define a new codec using `create-codec` which takes the same parameters
as `createCodec` in the msgpack module (see [msgpack-lite
documentation](https://github.com/kawanet/msgpack-lite)), also in a "clojurized"
form, e.g.:
```clojure
(def my-codec
  (create-codec :preset true))
```

### Encoding
Suppose you create your own type:
```clojure
(defrecord Game [title year platforms reviews])
(defrecord Review [site score])
(def botw 
     (->Game "Zelda: Breath of the Wild" 2018 :switch 
     [(->Review "GameSpot" 10) (->Review "Polygon" 10) (->Review "IGN" 10)]))
```
To encode this type, you need to define packers: A packer is a function that
takes a value and translates it into a buffer. For instance:
```clojure
(defn game-packer [{:keys [title year platforms reviews]}] 
  (encode [title year platforms reviews]))

(defn review-packer [{:keys [site score]}]
  (encode [site score]))
```
We chose to pack the values into arrays but a hash map would also be
possible. Next we create a new codec. Since we use ClojureScript-specific types
*within* our data structure (keywords), we extend `cls-codec`:
```clojure
(def game-codec 
  (-> (create-clj-codec) 
      (add-ext-packer! 0x40 Game game-packer)
      (add-ext-packer! 0x41 Review review-packer)))
```
`add-ext-packer!` takes as parameters the codec, a unique byte id for msgpack
encoding and decoding, the type and the packer function. Note the exclamation
mark in `add-ext-packer!` which indicates that this function is not pure. In
fact, the codec gets altered (by msgpack-lite).

We can now encode our data type:
```clojure
(encode botw :codec game-codec)
#object[Buffer [...]]
```
A little bit of "magic" happened in the background: We called `encode` with the
option `:codec game-codec`, whereas in the packers we call `encode` without any
further options, in particular, without the codec. What happens is that the
initial call rebinds `encode` to a function with the options from the initial
call automatically passed to `encode`. Any option passed with a recursive call
overwrites the initially passed option.

### Decoding
For decoding, we define unpackers which, not surprisingly, take a msgpack
buffer and produce the filled data structures. In our case:
```clojure
(defn game-unpacker [buffer] 
  (let [decoded-array (decode buffer)] 
    (apply ->Game decoded-array)))

(defn review-unpacker [buffer] 
  (as-> buffer _ 
        (decode _) 
        (apply ->Review _)))
```
And we can add it to our codec:
```clojure
(def game-codec 
  (-> (create-clj-codec) 
      (add-ext-packer! 0x40 Game game-packer)
      (add-ext-unpacker! 0x40 game-unpacker)
      (add-ext-packer! 0x41 Review review-packer)
      (add-ext-unpacker! 0x41 Review review-unpacker)))
```
Or shorter:
```clojure
(def game-codec 
  (-> (create-clj-codec) 
      (add-ext-packers! 0x40 Game game-packer game-unpacker)
      (add-ext-packers! 0x41 Review review-packer review-unpacker)))
```
We can now decode the type `Game` and `Review` using decode:
```clojure
=> (def botw-encoded (encode botw :codec game-codec))
=> (decode otw-encoded :codec game-codec)
{:title "Zelda: Breath of the Wild",
 :year 2018,
 :platforms "switch",
 :reviews
 [{"site" "GameSpot", "score" 10}
  {"site" "Polygon", "score" 10}
  {"site" "IGN", "score" 10}]}
```
Note that the underlying types here are really our records from above.

As with `encode` above, `decode` gets rebound in the recursive calls and any
options passed to the initial call are automatically passed to the all
subsequent calls.

## Encoding and Decoding Streams

To encode to and decode from a stream, respectively, `Transform`s can
be created by `create-encode-transform` and `create-decode-transform`. The
following example defines an `encode-to-file` and a `decode-from-file` function
for storing ClojureScript objects to a file and retrieving them back:
```Clojure
(ns your-namespace.stream-example
  (:require [cljs-msgpack-lite.core :as msp]))

(def fs (js/require "fs"))

(defn encode-to-file [data file-path & opts]
  (let [encode-transform (apply msp/create-encode-transform opts)
        fos (.createWriteStream fs file-path)]
    (.pipe encode-transform fos)
    (.write encode-transform data)
    (.end encode-transform)
    (.end fos)))

(defn decode-from-file [file-path callback & opts]
  (let [decode-transform (apply msp/create-decode-transform opts)
        fis (.createReadStream fs file-path)]
    (.pipe fis decode-transform)
    (.on fis "readable"
         (fn []
           (let [res (.read decode-transform)]
             (.end decode-transform)
             (callback res))))))
```
Note that each function creates a transform that is piped to a file stream. The
options passed to `create-encode-transform` and `create-decode-transform` are
the same as for `encode` and `decode`, respectively. For instance:
```Clojure
=> (def clj-codec (create-clj-codec))

=> (def some-fancy-cljs-data {:foo "bar" :array [1 2 3] :list '("a" "b" "c")})

=> (encode-to-file some-fancy-cljs-data "test.msp" :codec clj-codec)

=> (decode-from-file "test.msp"
        #(print % " = " some-fancy-cljs-data " ? " (= % some-fancy-cljs-data))
        :codec clj-codec :keywordize-keys true)
{:foo bar, :array [1 2 3], :list (a b c)}  =  {:foo bar, :array [1 2 3], :list (a b c)}  ?  true
```

# License

Copyright ©2018 Christopher Auer

Distributed under the MIT License. Please see the LICENSE file at the top level of this repo.
