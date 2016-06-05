(ns forms.test.common)

(def not-nil? [:not-nil (fn [v path full-data] (not (nil? v)))])
(def not-empty? [:not-empty (fn [v path full-data] (not (empty? v)))])
(def is-one? [:is-one (fn [v path full-data] (= 1 v))])
(def is-twitter? [:is-twitter (fn [v path full-data] (= v "twitter"))])
(def is-facebook? [:is-facebook (fn [v path full-data] (= v "facebook"))])
