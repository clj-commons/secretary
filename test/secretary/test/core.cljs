(ns secretary.test.core
  (:require [cemerick.cljs.test :as t]
            [secretary.core :as secretary])
  (:require-macros [cemerick.cljs.test :refer [deftest is testing]]
                   [secretary.macros :refer [defroute]]))

(defroute "/" [] 1)

(defroute "/users" [] "users")

(defroute "/users/:id" [id] id)

(defroute "/users/:id/food/:food" {:keys [id food]}
  (str id food))

(deftest route-test 
  (testing "dispatch!"
    (is (= (secretary/dispatch! "/")
           1))
    (is (= (secretary/dispatch! "/users")
           "users"))
    (is (= (secretary/dispatch! "/users/1")
           "1"))
    (is (= (secretary/dispatch! "/users/kevin/food/bacon")
           "kevinbacon"))))
