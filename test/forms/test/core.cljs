(ns forms.test.core
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.core :as core]))

(def not-nil [:not-nil (fn [a] (not (nil? a)))])
(def is-one [:is-one (fn [a] (= 1 a))])

(deftest simple-validator []
  (let [validator (core/validator {:input [not-nil]})]
    (is (= {"input" {:value nil :failed [:not-nil]}}
           (validator {})))))

(deftest multiple-validators []
  (let [validator (core/validator {:input [not-nil is-one]})]
    (is (= {"input" {:value nil :failed [:not-nil :is-one]}}
           (validator {})))))
