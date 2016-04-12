(ns forms.core
  (:require [clojure.string :as str]))

(enable-console-print!)

(defn ^:private keyword-or-integer [key]
  (if (re-matches #"[0-9]+" key)
    (js/parseInt key 10)
    (keyword key)))

(defn ^:private validation-key-to-path [key]
  (map keyword-or-integer (str/split (name key) ".")))

(defn ^:private validate-attr [value validators]
  (reduce (fn [failed [name validator]]
            (if (not (validator value))
              (conj failed name)
              failed)) [] validators))

(defn ^:private validate-map [input errors key attr-validators]
  (let [path (validation-key-to-path key)
        value (get-in input path)
        failed (validate-attr value attr-validators)]
    (if (pos? (count failed))
      (assoc-in errors path {:value value :failed failed})
      errors)))

(defn validator [validators]
  (fn [input]
    (let [errors (reduce-kv (partial validate-map input) {} validators)]
      (if (= errors {}) nil errors))))
