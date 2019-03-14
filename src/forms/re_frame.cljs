(ns forms.re-frame
  (:require [clojure.string :as str]
            [forms.util :refer [key-to-path]]
            [forms.dirty :refer [calculate-dirty-fields]]
            [re-frame.core :as rf]))

(defn ^:private init-state
  [data validator opts]
  {:errors {}
   :init-data data
   :data (or data {})
   :cached-dirty-key-paths #{}
   :dirty-key-paths #{}
   :validator validator
   :opts opts})

(defn errors-keypaths
  "Calculates the error key paths from the error map. It is used to mark
  all invalid key paths as dirty"
  ([data] (distinct (:results (errors-keypaths data [] {:results []}))))
  ([data path results]
   (reduce-kv (fn [m k v]
                (if (= k :$errors$)
                  (assoc m :results (conj (:results m) path))
                  (if (or (vector? v) (map? v))
                    (let [{:keys [results lengths]} m
                          new-path (conj path k)
                          child-paths (errors-keypaths v new-path m)
                          new-results (:results child-paths)]
                      {:results (concat results new-results)})
                    (if (nil? v)
                      m
                      (assoc m :results (conj (:results m) (conj path k))))))) results data)))

(defn form-path->db-path
  [form-path]
  (conj form-path [::form]))

(rf/reg-sub ::form
  (fn form [db [_ form-path]]
    (get-in db (form-path->db-path form-path))))

(rf/reg-sub ::data
  (fn data [db [_ form-path]]
    (get-in db (conj (form-path->db-path form-path) :data))))

(rf/reg-sub ::cached-dirty-key-paths
  (fn data [db [_ form-path]]
    (get-in db (conj (form-path->db-path form-path) :cached-dirty-key-paths))))

(rf/reg-sub ::dirty-key-paths
  (fn data [db [_ form-path]]
    (get-in db (conj (form-path->db-path form-path) :dirty-key-paths))))

(rf/reg-sub ::data-for-path
  (fn data-for-path [db [_ form-path key-path]]
    (get-in db (into (conj (form-path->db-path form-path) :data) (key-to-path key-path)))))

(rf/reg-sub ::errors
  (fn errors [db [_ form-path]]
    (get-in db (conj (form-path->db-path form-path) :errors))))

(defn errors-for-path
  [db [_ form-path key-path]]
  (let [path (key-to-path key-path)
        is-dirty? (contains? (:dirty-key-paths (get-in db (form-path->db-path form-path))) path)]
      (when is-dirty?
        (get-in db (into (conj (form-path->db-path form-path) :errors) (conj path :$errors$))))))

(rf/reg-sub ::errors-for-path
  errors-for-path)

(rf/reg-event-db ::init!
  (fn init! [db [_ form-path form-data]]
    (assoc-in db (form-path->db-path form-path) form-data)))

(defn mark-dirty!
  [form-state]
  (let [validator (:validator form-state)
        errors (validator (:data form-state))
        errors-keypaths (errors-keypaths errors)
        current-dirty-paths (:dirty-key-paths form-state)]
    (assoc form-state
           :cached-dirty-key-paths (set (concat (:cached-dirty-key-paths form-state) errors-keypaths))
           :dirty-key-paths (set errors-keypaths))))

(defn mark-dirty-paths!
  [form-state]
  (let [dirty-paths (calculate-dirty-fields (:init-data form-state) (:data form-state))]
    (assoc form-state :dirty-key-paths (set (concat dirty-paths
                                                      (:cached-dirty-key-paths form-state))))))

(defn validate!
  [db [_ form-path dirty-only?]]
  (let [dirty-db (if dirty-only?
                   (update-in db (form-path->db-path form-path) mark-dirty-paths!)
                   (update-in db (form-path->db-path form-path) mark-dirty!))
        validator (:validator (get-in dirty-db (form-path->db-path form-path)))]
    (update-in dirty-db (conj (form-path->db-path form-path)) assoc :errors (validator (:data (get-in dirty-db (form-path->db-path form-path)))))))

(rf/reg-event-db ::validate!
  validate!)

(rf/reg-event-db ::commit!
  (fn commit! [db [_ form-path]]
    (let [form-state (get-in db (form-path->db-path form-path))
          commit-fn (get-in form-state [:opts :on-commit])
          dirty-db (update-in db (form-path->db-path form-path) mark-dirty!)
          validated-db (validate! dirty-db [nil form-path])
          new-form-state (get-in validated-db (form-path->db-path form-path))]
      (commit-fn new-form-state)
      validated-db)))

(rf/reg-event-db ::update!
  (fn update! [db [_ form-path data]]
    (let [updated-db (update-in db (form-path->db-path form-path) assoc :data data)
          dirty-db (update-in updated-db (form-path->db-path form-path) mark-dirty-paths!)]
      (validate! dirty-db [nil form-path true]))))

(rf/reg-event-db ::mark-dirty!
  (fn mark-dirty!-ev [db [_ form-path]]
    (update-in db (form-path->db-path form-path) mark-dirty!)))

(rf/reg-event-db ::mark-dirty-paths!
  (fn mark-dirty-paths!-ev [db [_ form-path]]
    (update-in db (form-path->db-path form-path) mark-dirty-paths!)))

(rf/reg-sub ::dirty-paths-valid?
  (fn dirty-paths-valid? [db [_ form-path]]
    (let [form-state (get-in db (form-path->db-path form-path))
          errors (:errors form-state)
          dirty-paths (:dirty-key-paths form-state)
          valid-paths (take-while
                        (fn [path]
                          (nil? (get-in errors path))) dirty-paths)]
      (= (count valid-paths) (count dirty-paths)))))

(rf/reg-event-db ::clear-cached-dirty-key-paths!
  (fn mark-dirty-paths!-ev [db [_ form-path]]
    (update-in db (form-path->db-path form-path) assoc :cached-dirty-key-paths #{})))

(defn is-valid?
  [form-state]
  (let [errors (:errors form-state)]
    (= errors {})))

(rf/reg-sub ::is-valid?
  (fn is-valid?-ev [db [_ form-path]]
    (let [form-state (get-in db (form-path->db-path form-path))]
      (is-valid? form-state))))

(rf/reg-sub ::is-valid-path?
  (fn is-valid-path? [db [_ form-path key-path]]
    (nil? (errors-for-path db [nil form-path key-path]))))

(defn ^:private with-default-opts [opts]
  (merge {:on-commit (fn on-commit-placeholder [_])
          :auto-validate? false} opts))

(rf/reg-event-db ::reset-form!
  (fn reset-form! [db [_ form-path init-data*]]
    (let [{:keys [:init-data :validator :opts]} (get-in db (form-path->db-path form-path))]
      (if init-data*
        (update-in db (form-path->db-path form-path) merge (init-state init-data* validator (with-default-opts opts)))
        (update-in db (form-path->db-path form-path) merge (init-state init-data validator (with-default-opts opts)))))))

(defn set-value
  [db [_ form-path key-path value]]
  (let [form-state (get-in db (form-path->db-path form-path))
        auto-validate? (get-in form-state [:opts :auto-validate?])
        setted-db (assoc-in db (into (conj (form-path->db-path form-path) :data) (key-to-path key-path)) value)]
    (if auto-validate?
      (let [old-value (get-in db (into (conj (form-path->db-path form-path) :data) (key-to-path key-path)))]
        (if (= value old-value)
          setted-db
          (-> setted-db
              (update-in (form-path->db-path form-path) mark-dirty-paths!)
              (validate! [nil form-path true]))))
      setted-db)))

(rf/reg-event-db ::set! set-value)

(defn constructor
  "Form constructor. It accepts the following arguments:

  - `validator` - returned either by the `form.validator/validator` or `form.validator/comp-validators` function
  - `path` - path in the db where to put the form
  - `data` - initial data map
  - `opts` - map with the form options:
      + `:on-commit` - function to be called when the form is commited (by calling `(commit! form)`)
      + `:auto-validate?` - should the form be validated on any data change"

  ([validator] (partial constructor validator))
  ([validator path] (partial constructor validator path))
  ([validator path data] (constructor validator path data {}))
  ([validator path data opts]
   (rf/dispatch [::init! path (init-state data validator (with-default-opts opts))])))
