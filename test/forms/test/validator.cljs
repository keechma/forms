(ns forms.test.validator
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.validator :as v]
            [forms.test.common :refer [not-nil? is-one? is-twitter? is-facebook?]]))

(deftest simple-validator []
  (let [validator (v/validator {:input [not-nil?]})]
    (is (= {:input {:$errors$ {:value nil :failed [:not-nil]}}}
           (validator {})))))

(deftest multiple-validators []
  (let [validator (v/validator {:input [not-nil? is-one?]})]
    (is (= {:input {:$errors$ {:value nil :failed [:not-nil :is-one]}}}
           (validator {})))))

(deftest nested-path []
  (let [validator (v/validator {:user.username [not-nil?]
                                :user.foo [is-one?]})]
    (is (= {:user {:username {:$errors$ {:value nil :failed [:not-nil]}}}}
           (validator {:user {:foo 1}})))))

(deftest path-in-vector []
  (let [validator (v/validator {:user.social-networks.0.service [is-twitter?]
                                :user.username [not-nil?]
                                :user.social-networks.1.service [is-facebook?]})]
    (is (= {:user {:social-networks {0 {:service {:$errors$ {:value "snapchat" :failed [:is-twitter]}}}}
                   :username {:$errors$ {:value nil :failed [:not-nil]}}}}
           (validator {:user {:social-networks [{:service "snapchat"}
                                                {:service "facebook"}]}})))))

(deftest nested-in-list []
  (let [validator (v/validator {:user.social-networks.*.service [not-nil?]})]
    (is (= {:user {:social-networks {1 {:service {:$errors$ {:value nil :failed [:not-nil]}}}}}})
        (validator {:user {:social-networks [{:service "twitter"}
                                             {:service nil}]}}))))


(deftest deeply-nested-lists []
  (let [validator (v/validator {:user.profiles.*.social-networks.*.service [not-nil?]})]
    (is (= {:user {:profiles {1 {:social-networks {1 {:service {:$errors$ {:value nil :failed [:not-nil]}}}}}}}}
           (validator {:user {:profiles [{:social-networks [{:service "twitter"}]}
                                         {:social-networks [{:service "facebook"}
                                                            {:service nil}]}]}})))))

(deftest strings-in-vector []
  (let [validator (v/validator {:tags.* [not-nil?]})]
    (is (= {:tags {1 {:$errors$ {:value nil :failed [:not-nil]}}}}
           (validator {:tags ["foo" nil "bar"]})))))

(deftest comp-validators []
  (let [validator-a (v/validator {:username [not-nil?]})
        validator-b (v/validator {:password [not-nil?]})
        composed-validators (v/comp-validators validator-a validator-b)]
    (is (= {:username {:$errors$ {:value nil :failed [:not-nil]}}
            :password {:$errors$ {:value nil :failed [:not-nil]}}}
           (composed-validators {:real-name "Mihael Konjevic"})))))

(deftest comp-validators-for-vectors []
  (let [validator-a (v/validator {:tags.* [not-nil?]})
        validator-b (v/validator {:tags.* [is-twitter?]})
        composed-validators (v/comp-validators validator-a validator-b)]
    (is (= {:tags {1 {:$errors$ {:value nil :failed [:not-nil :is-twitter]}}}}
           (composed-validators {:tags ["twitter" nil]})))))

(deftest comp-validators-for-same-attr []
  (let [validator-a (v/validator {:social-network [not-nil?]})
        validator-b (v/validator {:social-network [is-twitter?]})
        composed-validators (v/comp-validators validator-a validator-b)]
    (is (= {:social-network {:$errors$ {:value nil :failed [:not-nil :is-twitter]}}}
           (composed-validators {})))))

(deftest nested-validators []
  (let [validator-a (v/validator {:username [not-nil?]
                                  :password [not-nil?]})
        validator-b (v/validator {:title [not-nil?]
                                  :user.username [is-twitter?]
                                  :user [validator-a]})]
    (is (= {:title {:$errors$ {:value nil :failed [:not-nil]}}
            :user {:username {:$errors$ {:value nil :failed [:is-twitter :not-nil]}}}}
           (validator-b {:title nil
                         :user {:username nil
                                :password "foo"}})))))

(deftest validator-full-data-path []
  (let [data {:username "mihael"
              :sites ["http://retroaktive.me"]
              :avatar {:url "http://example.com"}
              :social-networks [{:account "mihaelkonjevic"
                                 :tags [{:value "clojurescript"} {:value "clojure"}]}]}
        avatar-validator (v/validator {:url [[:_ (fn [value full-data path]
                                                    (is (= full-data data))
                                                    (is (= path [:avatar :url])))]]})
        validator (v/validator {:username
                                [[:_ (fn [value full-data path]
                                         (is (= full-data data))
                                         (is (= path [:username])))]]

                                :avatar [avatar-validator]

                                :sites.*
                                [[:_ (fn [value full-data path]
                                       (is (= full-data data))
                                       (is (= path [:sites 0])))]]

                                :social-networks.*.tags.*.value
                                [[:_ (fn [value full-data path]
                                       (is (= full-data data))
                                       (if (= value "clojurescript")
                                         (is (= path [:social-networks 0 :tags 0 :value]))
                                         (is (= path [:social-networks 0 :tags 1 :value]))))]]

                                :social-networks.*.account
                                [[:_ (fn [value full-data path]
                                       (is (= full-data data))
                                       (is (= path [:social-networks 0 :account])))]]})]
    (validator data)))

(deftest dependant-validator []
  (let [invalid-data {:email "test2@example.com"
                      :email-confirmation "test@example.com"}
        ok-data {:email "test@example.com"
                 :email-confirmation "test@example.com"}
        ;; This is the validator that will be applied both to the 
        ;; email and the email-confirmation fields. 
        confirmed-email? [:confirmed-email? (fn [value full-data path]
                                              (let [email (:email full-data)
                                                    email-confirmation (:email-confirmation full-data)]
                                                (= email email-confirmation)))]
        validator (v/validator {:email [confirmed-email?]
                                :email-confirmation [confirmed-email?]})]
    (= {:email {:$errors$ {:value "test2@example.com" :failed [:confirmed-email?]}}
        :email-confirmation {:$errors {:value "test@example.com" :failed [:confirmed-email?]}}}
       (validator invalid-data))
    (= nil (validator ok-data))))
