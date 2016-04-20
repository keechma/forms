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

(deftest data-for []
  (let [validator (v/validator {})
        form (core/constructor validator)
        inited-form (form {:username "foo"})
        data (core/data inited-form)]
    (is (= (core/data-for inited-form :username) "foo"))
    (swap! data assoc :username "bar")
    (is (= (core/data-for inited-form :username) "bar"))))

(deftest update! []
  (let [validator (v/validator {})
        form (core/constructor validator)
        inited-form (form {:username "foo"})]
    (core/update! inited-form {:username "bar"})
    (is (= (core/data-for inited-form :username) "bar"))
    (is (= #{[:username]} 
           (:dirty-key-paths @(core/state inited-form))))))
