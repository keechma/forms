(ns forms.util
  (:require [clojure.string :as str]))

(defn keyword-or-integer [key]
  (if (re-matches #"[0-9]+" key)
    (js/parseInt key 10)
    (keyword key)))

(defn key-to-path [key]
  (let [path (if (vector? key) key (str/split (name key) "."))]
    (vec (map keyword-or-integer path))))
