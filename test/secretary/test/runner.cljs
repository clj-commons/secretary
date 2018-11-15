(ns secretary.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [secretary.test.core]
            [secretary.test.codec]))

(doo-tests 'secretary.test.core
           'secretary.test.codec)
