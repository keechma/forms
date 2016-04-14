(ns forms.validator
  (:require [clojure.string :as str]))

(enable-console-print!)

(defn ^:private keyword-or-integer [key]
  (if (re-matches #"[0-9]+" key)
    (js/parseInt key 10)
    (keyword key)))

(defn ^:private validation-key-to-path [key]
  (map keyword-or-integer (str/split (name key) ".")))


(defn ^:private get-by-key [key next parent-data parent-errors]
  (let [data (get parent-data key)
        errors (or (get parent-errors key) {})
        res (next data errors)]
    (if (not (or (nil? res) (= {} res)))
      (assoc parent-errors key res)
      parent-errors)))

(defn ^:private get-list [next parent-data parent-errors]
  (let [data (reduce-kv (fn [m k v] 
                          (let [errors (or (get m k) {})
                                res (next v errors)]
                            (if (not (or (nil? res) (= {} res)))
                              (assoc m k res)
                              m))) parent-errors parent-data)]
    (if (= data {}) nil data)))

(defn ^:private validate-attr [value validators]
  (reduce (fn [failed [name validator]]
            (if (not (validator value))
              (conj failed name)
              failed)) [] validators))

(defn ^:private attr-errors [validators value errors]
  (let [failed (validate-attr value validators)]
    (when (pos? (count failed))
      (if (= errors {})
        (assoc errors :$errors$ {:value value :failed failed})
        (let [existing-failed (get-in errors [:$errors$ :failed])]
          (assoc-in errors [:$errors$ :failed] (into [] (concat existing-failed failed))))))))

(defn ^:private key-to-getter [key]
  (if (= key :*) get-list (partial get-by-key key)))

(defn ^:private make-validator [path validators]
  (let [iterator (reverse (map key-to-getter path))]
    (reduce (fn [acc v]
              (if (nil? acc)
                (partial v (partial attr-errors validators))
                (partial v acc))) nil iterator)))

(defn ^:private validate-map [input errors key attr-validators]
  (let [path (validation-key-to-path key)
        validator (make-validator path attr-validators)]
    (validator input errors)))

(defn ^:private validator-runner
  ([validators input] (validator-runner validators input {}))
  ([validators input errors]
   (let [new-errors (reduce-kv (partial validate-map input) errors validators)]
     (if (= new-errors {}) nil new-errors))))

(defn validator [validators]
  (partial validator-runner validators))

(defn comp-validators [& validators]
  (fn [input]
    (reduce (fn [acc v] (v input acc)) {} validators)))

