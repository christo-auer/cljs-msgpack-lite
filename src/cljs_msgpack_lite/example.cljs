(ns cljs-msgpack-lite.example
  [:require [cljs-msgpack-lite.core :refer [encode decode add-ext-packer! add-ext-unpacker! add-ext-packers!]]
            [cljs-msgpack-lite.clj-codec :refer [create-clj-codec]]])

(deftype Game [title year platforms reviews])
(deftype Review [site score])
(def botw 
     (->Game "Zelda: Breath of the Wild" 2018 :switch  
     [(->Review "GameSpot" 10) (->Review "Polygon" 10) (->Review "IGN" 10)]
     ))

(defn game-packer [{:keys [title year platforms reviews]}] 
  (encode [title year platforms reviews]))

(defn review-packer [{:keys [site score]}]
  (encode [site score]))

(defn game-unpacker [buffer] 
  (let [decoded-array (decode buffer)] 
    (apply ->Game decoded-array)))

(defn review-unpacker [buffer] 
  (as-> buffer _ 
        (decode _) 
        (apply ->Review _)))

;(extend-type Game
  ;IEncodeClojure
  ;(-js->clj [x options] x))

;(extend-type Review
  ;IEncodeClojure
  ;(-js->clj [x options] x))

(def game-codec 
  (-> (create-clj-codec) 
      (add-ext-packer! 0x40 Game game-packer)
      (add-ext-unpacker! 0x40 game-unpacker)
      (add-ext-packer! 0x41 Review review-packer)
      (add-ext-unpacker! 0x41 review-unpacker)))

(def b (encode botw :codec game-codec))

(decode b :codec game-codec :js->clj true)
