(ns forms.test.validator
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.validator :as v]))

(def not-nil [:not-nil (fn [v] (not (nil? v)))])
(def is-one [:is-one (fn [v] (= 1 v))])
(def is-twitter [:is-twitter (fn [v] (= v "twitter"))])
(def is-facebook [:is-facebook (fn [v] (= v "facebook"))])


(deftest simple-validator []
  (let [validator (v/validator {:input [not-nil]})]
    (is (= {:input {:$errors$ {:value nil :failed [:not-nil]}}}
           (validator {})))))

(deftest multiple-validators []
  (let [validator (v/validator {:input [not-nil is-one]})]
    (is (= {:input {:$errors$ {:value nil :failed [:not-nil :is-one]}}}
           (validator {})))))

(deftest nested-path []
  (let [validator (v/validator {:user.username [not-nil]
                                :user.foo [is-one]})]
    (is (= {:user {:username {:$errors$ {:value nil :failed [:not-nil]}}}}
           (validator {:user {:foo 1}})))))

(deftest path-in-vector []
  (let [validator (v/validator {:user.social-networks.0.service [is-twitter]
                                :user.username [not-nil]
                                :user.social-networks.1.service [is-facebook]})]
    (is (= {:user {:social-networks {0 {:service {:$errors$ {:value "snapchat" :failed [:is-twitter]}}}}
                   :username {:$errors$ {:value nil :failed [:not-nil]}}}}
           (validator {:user {:social-networks [{:service "snapchat"}
                                                {:service "facebook"}]}})))))

(deftest nested-in-list []
  (let [validator (v/validator {:user.social-networks.*.service [not-nil]})]
    (is (= {:user {:social-networks {1 {:service {:$errors$ {:value nil :failed [:not-nil]}}}}}})
        (validator {:user {:social-networks [{:service "twitter"}
                                             {:service nil}]}}))))


(deftest deeply-nested-lists []
  (let [validator (v/validator {:user.profiles.*.social-networks.*.service [not-nil]})]
    (is (= {:user {:profiles {1 {:social-networks {1 {:service {:$errors$ {:value nil :failed [:not-nil]}}}}}}}}
           (validator {:user {:profiles [{:social-networks [{:service "twitter"}]}
                                         {:social-networks [{:service "facebook"}
                                                            {:service nil}]}]}})))))
