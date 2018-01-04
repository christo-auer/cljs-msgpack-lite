(ns cljs-msgpack-lite.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [cljs-msgpack-lite.core-test]
              [cljs-msgpack-lite.clj-codec-test]))

(doo-tests 'cljs-msgpack-lite.core-test
           'cljs-msgpack-lite.clj-codec-test)

