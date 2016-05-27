(ns forms.test.test
  (:require [doo.runner :refer-macros [doo-tests]]
            [forms.test.core]
            [forms.test.validator]
            [forms.test.dirty]
            [forms.test.util]))

(doo-tests 'forms.test.core
           'forms.test.util
           'forms.test.validator
           'forms.test.dirty)
