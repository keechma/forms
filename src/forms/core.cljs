(ns forms.core
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [forms.util :as u :refer [key-to-path]]
            [forms.dirty :refer [calculate-dirty-fields]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(declare init-state)

(defprotocol IForm
  (init! [this] "Init the form")
  (state [this] "Returns inner state atom")
  (errors [this] [this all-errors?] "Returns errors atom")
  (errors-for-path [this key-path] "Returns errors for the key")
  (data [this] "Returns data atom")
  (data-for-path [this key-path] "Returns data for the key")
  (validate! [this] [this dirty-only?] "Validate form")
  (commit! [this] "Commit form")
  (update! [this data] "Update data")
  (mark-dirty! [this] [this is-dirty] "Mark all keys as dirty")
  (mark-dirty-paths! [this] "Mark dirty keys")
  (is-valid? [this] "Is the form in the valid state")
  (is-valid-path? [this key-path] "Is the path in the valid state")
  (dirty-paths-valid? [this] "Are the dirty fields valid")
  (reset-form! [this] [this init-data] "Reset form"))

(defrecord Form [state-atom validator opts]
  IForm
  (init! [this]
    (let [auto-validate? (get-in this [:opts :auto-validate?])]
      (remove-watch (state this) :__form__) 
      (when auto-validate?
        (add-watch (state this) :__form__
                   (fn [_ _ old-val new-val]
                     (let [old-data (:data old-val)
                           new-data (:data new-val)]
                       (when (not= old-data new-data)
                         (mark-dirty-paths! this)
                         (validate! this true))))))
      this))
  (state [this]
    (:state-atom this))
  (errors [this]
    (r/cursor (state this) [:errors]))
  (errors-for-path [this key-path]
    (reaction
     (let [path (key-to-path key-path)
           current-state @(state this)
           is-dirty? (contains? (:dirty-key-paths current-state) path)]
       (when is-dirty? (get-in @(errors this) (conj path :$errors$))))))
  (data [this]
    (r/cursor (state this) [:data]))
  (data-for-path [this key-path]
    (reaction
     (get-in @(data this) (key-to-path key-path))))
  (validate! [this]
    (validate! this false))
  (validate! [this dirty-only?]
    (if dirty-only?
      (mark-dirty-paths! this)
      (mark-dirty! this))
    (let [validator (:validator this)]
      (swap! (state this) assoc :errors (validator @(data this)))))
  (commit! [this]
    (let [commit-fn (get-in this [:opts :on-commit])]
      (mark-dirty! this)
      (validate! this)
      (commit-fn this)))
  (update! [this data]
    (swap! (state this) assoc :data data)
    (mark-dirty-paths! this)
    (validate! this))
  (mark-dirty! [this]
    (let [errors (validator @(data this))
          errors-keypaths (u/errors-keypaths errors)
          current-dirty-paths (:dirty-key-paths @(state this))]
      (swap! (state this) assoc :dirty-key-paths (set (concat current-dirty-paths errors-keypaths))))) 
  (mark-dirty-paths! [this]
    (let [current-state @(state this)]
      (swap! (state this) assoc :dirty-key-paths
             (calculate-dirty-fields (:init-data current-state) (:data current-state)))))
  (dirty-paths-valid? [this]
    (let [current-state @(state this)
          current-errors (:errors current-state)]
      (let [dirty-paths (:dirty-key-paths current-state)
            valid-paths (take-while
                         (fn [path]
                           (nil? (get-in current-errors path))) dirty-paths)]
        (= (count valid-paths) (count dirty-paths)))))
  (is-valid? [this]
    (= {} @(errors this)))
  (is-valid-path? [this key-path]
    (let [path (key-to-path key-path)
          current-state @(state this)
          errors (:errors current-state)
          dirty-paths (:dirty-key-paths current-state)
          invalid-path? (not (nil? (get-in errors path)))]
      (not (and (contains? dirty-paths path) invalid-path?))))
  (reset-form! [this]
    (reset-form! this (:init-data @(state this))))
  (reset-form! [this init-data]
    (reset! (state this) (init-state init-data))))

(defn init-state [data]
  {:errors {}
   :init-data data
   :data (or data {})
   :dirty-key-paths (set {})})

(defn with-default-opts [opts]
  (merge {:on-commit (fn [_])
          :auto-validate? false} opts))

(defn constructor
  ([validator] (partial constructor validator))
  ([validator data] (constructor validator data {}))
  ([validator data opts]
   (init! (->Form (r/atom (init-state data)) validator (with-default-opts opts)))))
