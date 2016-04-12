(ns forms.test.core
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.core :as core]))

(def not-nil [:not-nil (fn [v] (not (nil? v)))])
(def is-one [:is-one (fn [v] (= 1 v))])
(def is-twitter [:is-twitter (fn [v] (= v "twitter"))])
(def is-facebook [:is-facebook (fn [v] (= v "facebook"))])


(deftest simple-validator []
  (let [validator (core/validator {:input [not-nil]})]
    (is (= {:input {:value nil :failed [:not-nil]}}
           (validator {})))))

(deftest multiple-validators []
  (let [validator (core/validator {:input [not-nil is-one]})]
    (is (= {:input {:value nil :failed [:not-nil :is-one]}}
           (validator {})))))

(deftest nested-path []
  (let [validator (core/validator {:user.username [not-nil]
                                   :user.foo [is-one]})]
    (is (= {:user {:username {:value nil :failed [:not-nil]}}}
           (validator {:user {:foo 1}})))))

(deftest path-in-vector []
  (let [validator (core/validator {:user.social-networks.0.service [is-twitter]
                                   :user.social-networks.1.service [is-facebook]})]
       (is (= {:user {:social-networks {0 {:service {:value "snapchat" :failed [:is-twitter]}}}}}
              (validator {:user {:social-networks [{:service "snapchat"}
                                                   {:service "facebook"}]}})))))
