(defproject cljs-msgpack-lite "0.1.6"
  :description "cljs-msgpack-lite is a lightweight and convenient wrapper around msgpack-lite for ClojureScript."
  :url "https://github.com/christo-auer/cljs-msgpack-lite"

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [cljsjs/msgpack-lite "0.1.26-0"]
                 [org.clojure/core.async "0.4.474"]
                 [camel-snake-kebab "0.4.0"]
                 [org.clojure/test.check "0.10.0-alpha2"]
                 [org.clojure/clojurescript "1.9.908"]]

  :plugins [[lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [lein-figwheel "0.5.13"]
            [lein-doo "0.1.8"]
            [lein-npm "0.6.2"]]

  :npm {:devDependencies [[ws "3.3.2"]]}

  :source-paths ["src" "test"]

  :clean-targets ["server.js"
                  "target"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :figwheel true
              :compiler {
                :main cljs-msgpack-lite.core
                :asset-path "target/js/compiled/dev"
                :output-to "target/js/compiled/cljs_msgpack_lite.js"
                :output-dir "target/js/compiled/dev"
                :target :nodejs
                :optimizations :none
                :source-map-timestamp true}}
             {:id "test"
              :source-paths ["src" "test"]
              :compiler {
                         :main cljs-msgpack-lite.runner
                         :output-to "target/js/compiled/cljs_msgpack_lite_test.js"
                         :target :nodejs
                         :optimizations :none}}             
             {:id "prod"
              :source-paths ["src"]
              :compiler {
                :output-to "server.js"
                :output-dir "target/js/compiled/prod"
                :target :nodejs
                :optimizations :simple}}]}

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.13"]
                                  [com.cemerick/piggieback "0.2.2"]]
                   :source-paths ["src" "dev"]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
