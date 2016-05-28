(ns forms.validator
  (:require [forms.util :refer [key-to-path]]))

(enable-console-print!)

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
  (reduce (fn [failed v]
            (let [[name validator] v]
                (if (not (validator value))
                  (conj failed name)
                  failed))) [] validators))

(defn ^:private validate-with-nested-validators [nested-validators value errors]
  (if (not (empty? nested-validators))
    (reduce (fn [acc v]
              (v value acc)) errors nested-validators)
    errors))

(defn ^:private attr-errors [validators value errors]
  (let [nested-validators (vec (filter fn? validators))
        normal-validators (vec (filter (complement fn?) validators))
        with-nested-errors (validate-with-nested-validators nested-validators value errors)
        failed (validate-attr value normal-validators)]
    (if (pos? (count failed))
      {:$errors$ {:value value
                  :failed (concat (or (get-in with-nested-errors [:$errors$ :failed]) []) failed)}}
      with-nested-errors)))

(defn ^:private key-to-getter [key]
  (if (= key :*) get-list (partial get-by-key key)))

(defn ^:private make-validator [path validators]
  (let [iterator (reverse (map key-to-getter path))]
    (reduce (fn [acc v]
              (if (nil? acc)
                (partial v (partial attr-errors validators))
                (partial v acc))) nil iterator)))

(defn ^:private validate-map [input errors key attr-validators]
  (let [path (key-to-path key)
        validator (make-validator path attr-validators)]
    (validator input errors)))

(defn ^:private validator-runner
  ([validators input] (validator-runner validators input {}))
  ([validators input errors]
   (reduce-kv (partial validate-map input) errors validators)))

(defn validator
  "Creates a form validator. Validator is a map where keys represent the path
  to data that will be validated and the value is a vector of validator definitions.

  Validator definitions are tuples where the first element is a validator name and the
  second one is the validator function. Validator function receives the value for the key
  path and returns a boolean. `true` if the data is valid and `false` if it's invalid.

  `validator` returns the function that accepts the data and returns the map of validation
  errors.

  **Simple example:**

  ```clojure
  (def not-empty [:not-empty (fn [v] (not (empty? v)))])
  (def form-validator-1 (validator {:username [not-empty]}))
  
  (form-validator-1 {:username \"\"})
  ;; returns {:username {:$errors$ {:value \"\" :failed [:not-empty]}}}
  ```

  **Validators can validate nested paths:**

  ```clojure
  (def form-validator-2 (validator {:user.name [not-empty]}))
  (form-validator-2 {:user {:username \"\"}})
  ;; returns {:user {:username {:$errors$ {:value \"\" :failed [:not-empty]}}}}
  ```

  **Validators can validate objects in the list:**

  ```clojure
  (def form-validator-3 (validator {:user.accounts.*.network [not-empty]}))
  (form-validator-3 {:user {:accounts [{:network \"\"}]}})
  ;; returns {:user {:accounts {0 {:network {:$errors$ {:value \"\" :failed [:not-empty]}}}}}}
  ```

  **Validators can validate values in the list:**

  ```clojure
  (def form-validator-4 (validator {:user.phone-numbers.* [not-empty]}))
  (form-validator-3 {:user {:phone-numbers [\"\"]}})
  ;; returns {:user {:phone-numbers {0 {:$errors$ {:value \"\" :failed [:not-empty]}}}}}
  ```

  **Validators can be nested inside other validators:**

  ```clojure
  (def user-validator (validator {:username [not-empty]}))
  (def article-validator (validator {:title [not-empty]
                                     :user [user-validator]}))

  (article-validator {:title \"\" :user {:username \"\"}})
  ;; returns {:title {:$errors {:value \"\" :failed [:not-empty]}}
  ;;          :user {:username {:$errors$ {:value \"\" :failed [:not-nil]}}}}
  ```

  Features provided by the validator ensure that you can validate any data structure, no matter how deeply nested it is. You can also create small focused validators that can be nested or composed which ensures
  that your validation logic stays DRY and allows reuse of the validators.
  "
  [validators]
  (partial validator-runner validators))

(defn comp-validators
  "Creates a validator that is a composition of the validators passed as the arguments:

  ```clojure
  (def not-empty [:not-empty (fn [v] (not (empty? v)))])

  (def username-validator (validator {:username [not-empty]}))
  (def password-validator (validator {:password [not-empty]}))

  (def user-validator (comp-validators username-validator password-validator))

  (user-validator {:username \"\" :password \"\"})
  ;; returns {:username {:$errors$ {:value \"\" :failed [:not-empty]}}
  ;;          :password {:$errors$ {:value \"\" :failed [:not-empty]}}}
  ```"  
  [& validators]
  (fn [input]
    (reduce (fn [acc v] (or (v input acc) {})) {} validators)))
