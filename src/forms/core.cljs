(ns forms.core
  (:require [clojure.string :as str]))

(enable-console-print!)

(defn ^:private validation-key-to-path [key]
  (str/split (name key) "."))

(defn ^:private validate-attr [value validators]
  (reduce (fn [failed [name validator]]
            (if (not (validator value))
              (conj failed name)
              failed)) [] validators))

(defn ^:private validate-map [errors key attr-validators]
  (let [path (validation-key-to-path key)
        value (get-in input path)
        failed (validate-attr value attr-validators)]
    (if (pos? (count failed))
      (assoc-in errors path {:value value :failed failed})
      errors)))

(defn validator [validators]
  (fn [input]
    (let [errors (reduce-kv validate-map {} validators)]
      (if (= errors {}) nil errors))))
