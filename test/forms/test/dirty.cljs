(ns forms.test.dirty
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.dirty :refer [calculate-dirty-fields]]))

(deftest dirty-fields-calculation []
  (let [dirty-fields (calculate-dirty-fields
                      {:username "retro"
                       :password "foo"
                       :social-networks [{:service "twitter"
                                          :test [{:aa "bb"}]
                                          :foo "bar"}]}
                      {:username "mihaelkonjevic"
                       :password "foo"
                       :social-networks [{:service "twitter"
                                          :test [{:aa "bb" :cc "dd"}]
                                          :username "aaa"}
                                         {:service "facebook"}]})]
    (is (= dirty-fields
           #{[:username] [:social-networks 0 :foo] [:social-networks 0 :username] [:social-networks 0 :test 0 :cc] [:social-networks 1 :service] [:social-networks] [:social-networks 0 :test]}))))

