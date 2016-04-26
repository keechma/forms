(ns forms.test.core
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.validator :as v]
            [forms.core :as core]
            [forms.test.common :refer [not-nil is-one is-twitter is-facebook]]))

(deftest validate! []
  (let [validator (v/validator {:username [not-nil]
                                :password [not-nil]})
        form (core/constructor validator)
        inited-form (form {})
        data (core/data inited-form)]
    (is (core/is-valid? inited-form))
    (swap! data assoc :username "retro")
    (core/validate! inited-form)
    (is (= @(core/errors inited-form))
        {:password {:$errors$ {:value nil :failed [:not-nil]}}})
    (is (not (core/is-valid? inited-form)))
    (is (core/is-valid-path? inited-form :username))
    (is (not (core/is-valid-path? inited-form :password)))))

(deftest auto-validate []
  (let [validator (v/validator {:username [not-nil]
                                :password [not-nil]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})
        data (core/data inited-form)]
    (is (core/is-valid? inited-form))
    (swap! data assoc :username "retro")
    (is (not (core/is-valid? inited-form)))
    (is (nil? (core/errors-for-path inited-form :password)))
    (core/commit! inited-form)
    (is (not (nil? (core/errors-for-path inited-form :password))))))

(deftest data-for-path []
  (let [validator (v/validator {})
        form (core/constructor validator)
        inited-form (form {:username "foo"})
        data (core/data inited-form)]
    (is (= (core/data-for-path inited-form :username) "foo"))
    (swap! data assoc :username "bar")
    (is (= (core/data-for-path inited-form :username) "bar"))))

(deftest update! []
  (let [validator (v/validator {})
        form (core/constructor validator)
        inited-form (form {:username "foo"})]
    (core/update! inited-form {:username "bar"})
    (is (= (core/data-for-path inited-form :username) "bar"))
    (is (= #{[:username]}
           (:dirty-key-paths @(core/state inited-form))))))

(deftest commit! []
  (let [commit-called (atom 0)
        validator (v/validator {:username [not-nil]})
        on-commit (fn [f] 
                    (when (= 0 @commit-called)
                      (is (not (core/is-valid? f))))
                    (when (= 1 @commit-called)
                      (is (core/is-valid? f)))
                    (swap! commit-called inc))
        form (core/constructor validator)
        inited-form (form {} {:on-commit on-commit})]
    (core/commit! inited-form)
    (swap! (core/data inited-form) assoc :username "foo")
    (core/commit! inited-form)
    (is (= 2 @commit-called))))

(deftest errors []
  (let [validator (v/validator {:username [not-nil]
                                :password [not-nil]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})]
    (is (= @(core/errors inited-form) {}))
    (is (= nil (core/errors-for-path inited-form :username)))
    (is (= nil (core/errors-for-path inited-form :password)))
    (swap! (core/data inited-form) assoc :username "foo")
    (is (= @(core/errors inited-form) {:password {:$errors$ {:value nil :failed [:not-nil]}}}))
    (is (= nil (core/errors-for-path inited-form :password)))
    (core/mark-dirty! inited-form)
    (is (= {:value nil :failed [:not-nil]} (core/errors-for-path inited-form :password)))))

(deftest dirty-paths-valid? []
  (let [validator (v/validator {:username [not-nil]
                                :password [not-nil]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})]
    (is (core/dirty-paths-valid? inited-form))
    (swap! (core/data inited-form) assoc :username "foo")
    (is (core/dirty-paths-valid? inited-form))
    (swap! (core/data inited-form) assoc :password nil)
    (is (not (core/dirty-paths-valid? inited-form)))))

(deftest dirty-paths-valid?-when-all-dirty []
  (let [validator (v/validator {:username [not-nil]
                                :password [not-nil]})
        form (core/constructor validator)
        inited-form (form {})]
    (is (core/dirty-paths-valid? inited-form))
    (core/validate! inited-form)
    (is (not (core/dirty-paths-valid? inited-form)))))

(deftest is-valid-path? []
  (let [validator (v/validator {:username [not-nil]})
        form (core/constructor validator)
        inited-form (form {})]
    (is (core/is-valid-path? inited-form :username))
    (swap! (core/data inited-form) assoc :username nil)
    (core/validate! inited-form true)
    (is (not (core/is-valid-path? inited-form :username)))))

(deftest reset-form! []
  (let [validator (v/validator {:username [not-nil]})
        form (core/constructor validator)
        inited-form (form {})]
    (swap! (core/data inited-form) assoc :username nil)
    (core/validate! inited-form)
    (is (not (core/is-valid? inited-form)))
    (core/reset-form! inited-form)
    (is (= {} @(core/data inited-form)))
    (is (core/is-valid? inited-form))
    (core/validate! inited-form)
    (is (not (core/is-valid? inited-form)))
    (core/reset-form! inited-form {:username "retro"})
    (is (core/is-valid? inited-form))
    (core/validate! inited-form)
    (is (core/is-valid? inited-form))))
