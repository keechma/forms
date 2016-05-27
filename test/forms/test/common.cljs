(ns forms.test.common)

(def not-nil [:not-nil (fn [v] (not (nil? v)))])
(def not-empty [:not-empty (fn [v] (not (empty? v)))])
(def is-one [:is-one (fn [v] (= 1 v))])
(def is-twitter [:is-twitter (fn [v] (= v "twitter"))])
(def is-facebook [:is-facebook (fn [v] (= v "facebook"))])
