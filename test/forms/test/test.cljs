(ns forms.test.test
  (:require [doo.runner :refer-macros [doo-tests]]
            [forms.test.core]
            [forms.test.dirty]))

(doo-tests 'forms.test.core
           'forms.test.dirty)
