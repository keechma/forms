(ns forms.test.util
  (:require [cljs.test :refer-macros [deftest is]]
            [forms.util :as u]))

(deftest errors-keypaths []
  (is (= [[:user :username] [:user :social-networks 0 :network] [:user :phone-numbers 0]]
         (u/errors-keypaths {:user {:username :retro
                             :social-networks [{:network :twitter}]
                             :phone-numbers [0000]}}))))

