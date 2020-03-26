(ns forms.test.re-frame
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.validator :as v]
            [forms.re-frame :as f]
            [day8.re-frame.test :as rf-test]
            [forms.test.common :refer [not-nil? not-empty? is-one? is-twitter? is-facebook?]]
            [re-frame.core :as rf]))

(deftest errors-keypaths []
  (is (= [[:user :username] [:user :social-networks 0 :network] [:user :phone-numbers 0]]
         (f/errors-keypaths {:user {:username :retro
                             :social-networks [{:network :twitter}]
                             :phone-numbers [0000]}}))))

(deftest validate!
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-nil?]
                                  :password [not-nil?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {})
          errors (rf/subscribe [::f/errors form-path])
          is-valid? (rf/subscribe [::f/is-valid? form-path])
          valid-path-username (rf/subscribe [::f/is-valid-path? form-path :username])
          valid-path-password (rf/subscribe [::f/is-valid-path? form-path :password])]
      (is @is-valid?)
      (rf/dispatch [::f/set! form-path :username "retro"])
      (rf/dispatch [::f/validate! form-path])
      (is (= {:password {:$errors$ {:value nil :failed [:not-nil]}}}
             @errors))
      (is (not @is-valid?))
      (is @valid-path-username)
      (is (not @valid-path-password)))))

(deftest auto-validate!
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-nil?]
                                  :password [not-nil?]})
          form-path [:my-form :nested]
          form (f/constructor validator form-path)
          _ (form {} {:auto-validate? true})
          is-valid? (rf/subscribe [::f/is-valid? form-path])
          errors-for-password (rf/subscribe [::f/errors-for-path form-path :password])
          form (rf/subscribe [::f/form form-path])
          ]
      (is @is-valid?)
      (rf/dispatch [::f/set! form-path :username "retro"])
      (is (not @is-valid?))
      (is (nil? @errors-for-password))
      (rf/dispatch [::f/commit! form-path])
      (is (not (nil? @errors-for-password))))))

(deftest data-for-path
  (rf-test/run-test-sync
    (let [validator (v/validator {})
          form-path [:my-form :nested]
          form (f/constructor validator form-path)
          _ (form {:username "foo"})
          data-for-username (rf/subscribe [::f/data-for-path form-path :username])]
      (is (= "foo" @data-for-username))
      (rf/dispatch [::f/set! form-path :username "bar"])
      (is (= "bar" @data-for-username)))))

(deftest update!
  (rf-test/run-test-sync
    (let [validator (v/validator {})
          form-path [:my-form :nested]
          form (f/constructor validator form-path)
          _ (form {:username "foo"})
          data-for-username (rf/subscribe [::f/data-for-path form-path :username])]
      (rf/dispatch [::f/update! form-path {:username "bar"}])
      (is (= "bar" @data-for-username)))))

(deftest commit!
  (rf-test/run-test-sync
    (let [commit-called (atom 0)
          form-path [:my-form :nested]
          validator (v/validator {:username [not-nil?]})
          is-valid? (rf/subscribe [::f/is-valid? form-path])
          on-commit (fn [f]
                      (when (= 0 @commit-called)
                        (is (not (f/is-valid? f))))
                      (when (= 1 @commit-called)
                        (is (f/is-valid? f)))
                      (swap! commit-called inc))
          form (f/constructor validator form-path)
          _ (form {} {:on-commit on-commit})]
      (rf/dispatch [::f/commit! form-path])
      (rf/dispatch [::f/set! form-path :username "foo"])
      (rf/dispatch [::f/commit! form-path])
      (is (= 2 @commit-called)))))

(deftest errors
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-nil?]
                                  :password [not-nil?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {} {:auto-validate? true})
          errors (rf/subscribe [::f/errors form-path])
          errors-for-username (rf/subscribe [::f/errors-for-path form-path :username])
          errors-for-password (rf/subscribe [::f/errors-for-path form-path :password])]
      (is (= @errors {}))
      (is (= nil @errors-for-username))
      (is (= nil @errors-for-password))
      (rf/dispatch [::f/set! form-path :username "foo"])
      (is (= @errors {:password {:$errors$ {:value nil :failed [:not-nil]}}}))
      (is (= nil @errors-for-password))
      (rf/dispatch [::f/mark-dirty! form-path])
      (is (= {:value nil :failed [:not-nil]} @errors-for-password)))))

(deftest dirty-paths-valid?
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-empty?]
                                  :password [not-empty?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {} {:auto-validate? true})
          dirty-paths-valid? (rf/subscribe [::f/dirty-paths-valid? form-path])]
      (is @dirty-paths-valid?)
      (rf/dispatch [::f/set! form-path :username "foo"])
      (is @dirty-paths-valid?)
      (rf/dispatch [::f/set! form-path :password ""])
      (is (not @dirty-paths-valid?)))))

(deftest dirty-paths-valid?-when-all-dirty
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-nil?]
                                  :password [not-nil?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {})
          dirty-paths-valid? (rf/subscribe [::f/dirty-paths-valid? form-path])]
      (is @dirty-paths-valid?)
      (rf/dispatch [::f/validate! form-path])
      (is (not @dirty-paths-valid?)))))

(deftest is-valid-path?
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-empty?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {})
          valid-path-username (rf/subscribe [::f/is-valid-path? form-path :username])
          ]
      (is @valid-path-username)
      (rf/dispatch [::f/set! form-path :username ""])
      (rf/dispatch [::f/validate! form-path true])
      (is (not @valid-path-username)))))

(deftest reset-form!
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-nil?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {})
          is-valid? (rf/subscribe [::f/is-valid? form-path])
          data (rf/subscribe [::f/data form-path])]
      (rf/dispatch [::f/set! form-path :username nil])
      (rf/dispatch [::f/validate! form-path])
      (is (not @is-valid?))
      (rf/dispatch [::f/reset-form! form-path])
      (is (= {} @data))
      (is @is-valid?)
      (rf/dispatch [::f/validate! form-path])
      (is (not @is-valid?))
      (rf/dispatch [::f/reset-form! form-path {:username "retro"}])
      (is @is-valid?)
      (rf/dispatch [::f/validate! form-path])
      (is @is-valid?))))

(deftest validate-dirty-keys-behavior
  (rf-test/run-test-sync
    (let [validator (v/validator {:username [not-empty?]
                                  :password [not-empty?]
                                  :phone-numbers.* [not-empty?]})
          form-path [:my-form]
          form (f/constructor validator form-path)
          _ (form {} {:auto-validate? true})
          cached-dirty-key-paths (rf/subscribe [::f/cached-dirty-key-paths form-path])
          dirty-key-paths (rf/subscribe [::f/dirty-key-paths form-path])
          ]
      (rf/dispatch [::f/set! form-path :username ""])
      (rf/dispatch [::f/validate! form-path])
      (is (= #{[:username] [:password]}
             @cached-dirty-key-paths))
      (rf/dispatch [::f/set! form-path :phone-numbers [nil]])
      (is (= #{[:username] [:password] [:phone-numbers]}
             @dirty-key-paths))
      (rf/dispatch [::f/validate! form-path])
      (is (= #{[:username] [:password] [:phone-numbers 0]}
             @dirty-key-paths
             @cached-dirty-key-paths)))))
