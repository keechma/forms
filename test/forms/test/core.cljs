(ns forms.test.core
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.validator :as v]
            [forms.core :as core]
            [forms.test.common :refer [not-nil? not-empty? is-one? is-twitter? is-facebook?]]
            [forms.util :as util]))

(deftest errors-keypaths []
  (is (= [[:user :username] [:user :social-networks 0 :network] [:user :phone-numbers 0]]
         (core/errors-keypaths {:user {:username :retro
                                       :social-networks [{:network :twitter}]
                                       :phone-numbers [0000]}}))))


(deftest validate! []
  (let [validator (v/validator {:username [not-nil?]
                                :password [not-nil?]})
        form (core/constructor validator)
        inited-form (form {})
        data (core/data inited-form)]
    (is (core/is-valid? inited-form))
    (swap! data assoc :username "retro")
    (core/validate! inited-form)
    (is (= @(core/errors inited-form))
        {:password {:$errors$ {:value nil :failed [:not-nil]}}})
    (is (not @(core/is-valid? inited-form)))
    (is @(core/is-valid-path? inited-form :username))
    (is (not @(core/is-valid-path? inited-form :password)))))

(deftest auto-validate []
  (let [validator (v/validator {:username [not-nil?]
                                :password [not-nil?]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})
        data (core/data inited-form)]
    (is @(core/is-valid? inited-form))
    (swap! data assoc :username "retro")
    (is (not @(core/is-valid? inited-form)))
    (is (nil? @(core/errors-for-path inited-form :password)))
    (core/commit! inited-form)
    (is (not (nil? @(core/errors-for-path inited-form :password))))))

(deftest data-for-path []
  (let [validator (v/validator {})
        form (core/constructor validator)
        inited-form (form {:username "foo"})
        data (core/data inited-form)]
    (is (= @(core/data-for-path inited-form :username) "foo"))
    (swap! data assoc :username "bar")
    (is (= @(core/data-for-path inited-form :username) "bar"))))

(deftest update! []
  (let [validator (v/validator {})
        form (core/constructor validator)
        inited-form (form {:username "foo"})]
    (core/update! inited-form {:username "bar"})
    (is (= @(core/data-for-path inited-form :username) "bar"))))

(deftest commit! []
  (let [commit-called (atom 0)
        validator (v/validator {:username [not-nil?]})
        on-commit (fn [f] 
                    (when (= 0 @commit-called)
                      (is (not @(core/is-valid? f))))
                    (when (= 1 @commit-called)
                      (is @(core/is-valid? f)))
                    (swap! commit-called inc))
        form (core/constructor validator)
        inited-form (form {} {:on-commit on-commit})]
    (core/commit! inited-form)
    (swap! (core/data inited-form) assoc :username "foo")
    (core/commit! inited-form)
    (is (= 2 @commit-called))))

(deftest errors []
  (let [validator (v/validator {:username [not-nil?]
                                :password [not-nil?]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})]
    (is (= @(core/errors inited-form) {}))
    (is (= nil @(core/errors-for-path inited-form :username)))
    (is (= nil @(core/errors-for-path inited-form :password)))
    (swap! (core/data inited-form) assoc :username "foo")
    (is (= @(core/errors inited-form) {:password {:$errors$ {:value nil :failed [:not-nil]}}}))
    (is (= nil @(core/errors-for-path inited-form :password)))
    (core/mark-dirty! inited-form)
    (is (= {:value nil :failed [:not-nil]} @(core/errors-for-path inited-form :password)))))

(deftest dirty-paths-valid? []
  (let [validator (v/validator {:username [not-empty?]
                                :password [not-empty?]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})]
    (is @(core/dirty-paths-valid? inited-form))
    (swap! (core/data inited-form) assoc :username "foo")
    (is @(core/dirty-paths-valid? inited-form))
    (swap! (core/data inited-form) assoc :password "")
    (is (not @(core/dirty-paths-valid? inited-form)))))

(deftest dirty-paths-valid?-when-all-dirty []
  (let [validator (v/validator {:username [not-nil?]
                                :password [not-nil?]})
        form (core/constructor validator)
        inited-form (form {})]
    (is @(core/dirty-paths-valid? inited-form))
    (core/validate! inited-form)
    (is (not @(core/dirty-paths-valid? inited-form)))))

(deftest is-valid-path? []
  (let [validator (v/validator {:username [not-empty?]})
        form (core/constructor validator)
        inited-form (form {})]
    (is @(core/is-valid-path? inited-form :username))
    (swap! (core/data inited-form) assoc :username "")
    (core/validate! inited-form true)
    (is (not @(core/is-valid-path? inited-form :username)))))

(deftest reset-form! []
  (let [validator (v/validator {:username [not-nil?]})
        form (core/constructor validator)
        inited-form (form {})]
    (swap! (core/data inited-form) assoc :username nil)
    (core/validate! inited-form)
    (is (not @(core/is-valid? inited-form)))
    (core/reset-form! inited-form)
    (is (= {} @(core/data inited-form)))
    (is @(core/is-valid? inited-form))
    (core/validate! inited-form)
    (is (not @(core/is-valid? inited-form)))
    (core/reset-form! inited-form {:username "retro"})
    (is @(core/is-valid? inited-form))
    (core/validate! inited-form)
    (is @(core/is-valid? inited-form))))

(deftest validate-dirty-keys-behavior []
  (let [validator (v/validator {:username [not-empty?]
                                :password [not-empty?]
                                :phone-numbers.* [not-empty?]})
        form (core/constructor validator)
        inited-form (form {} {:auto-validate? true})]
    (swap! (core/data inited-form) assoc :username "")
    (core/validate! inited-form)
    (is (= #{[:username] [:password]}
           (:cached-dirty-key-paths @(core/state inited-form))))
    (swap! (core/data inited-form) assoc :phone-numbers [nil])
    (is (= #{[:username] [:password] [:phone-numbers]}
           (:dirty-key-paths @(core/state inited-form))))
    (core/validate! inited-form)
    (is (= #{[:username] [:password] [:phone-numbers 0]}
           (:dirty-key-paths @(core/state inited-form))
           (:cached-dirty-key-paths @(core/state inited-form))))))

(deftest key-to-path []
  (is (= [:foo] (util/key-to-path :foo)))
  (is (= [:foo :bar]) (util/key-to-path :foo.bar))
  (is (= [:foo/bar] (util/key-to-path :foo/bar)))
  (is (= [:foo/bar] (util/key-to-path [:foo/bar])))
  (is (= [:foo/bar :baz] (util/key-to-path :foo/bar.baz)))
  (is (= [:foo/bar :baz/qux] (util/key-to-path "foo/bar.baz/qux"))))

(deftest stress-test []
  (let [errors {:listOfPreferredProviderNetworks 
                {:$errors$ {:value nil,:failed [:not-empty]}},

                :healthInsuranceProvider
                {:$errors$ {:value nil,:failed [:not-empty]}}

                :email
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :listInNetworkHospitals 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :employerAddress2 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :employerState 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :employerZipCode 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :otherFees
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :administrationFees
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :monthlyPremiumForEmployeesAndDependents 
                {:$errors$ {:value nil,:failed [:not-empty]}}

                :nrEmployeesEnrolled 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :employerCity 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :employerPrimaryPhone
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :firstName 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :nrOfEmployees
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :monthlyPremiumForEmployees 
                {:$errors$ {:value nil,:failed [:not-empty]}}

                :employerAddress1 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :lastName 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :stopLossAllocation 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :healthCareCost
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :nameOfEmployer
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :employerLocations
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :nrEmployeesAndDependentsEnrolled 
                {:$errors$ {:value nil,:failed [:not-empty]}}

                :employerCountry 
                {:$errors$ {:value nil, :failed [:not-empty]}}

                :isConfidentialAgreementAproved
                {:$errors$ {:value nil,:failed [:true]}}
                }]
    (is (= [[:listOfPreferredProviderNetworks]
            [:healthInsuranceProvider] 
            [:email]
            [:listInNetworkHospitals]
            [:employerAddress2] 
            [:employerState]
            [:employerZipCode]
            [:otherFees]
            [:administrationFees] 
            [:monthlyPremiumForEmployeesAndDependents]
            [:nrEmployeesEnrolled]
            [:employerCity]
            [:employerPrimaryPhone]
            [:firstName] 
            [:nrOfEmployees] 
            [:monthlyPremiumForEmployees]
            [:employerAddress1]
            [:lastName] 
            [:stopLossAllocation] 
            [:healthCareCost] 
            [:nameOfEmployer]
            [:employerLocations]
            [:nrEmployeesAndDependentsEnrolled]
            [:employerCountry] 
            [:isConfidentialAgreementAproved]]
           (core/errors-keypaths errors)))))
