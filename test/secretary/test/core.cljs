(ns secretary.test.core
  (:require [cemerick.cljs.test :as t]
            [secretary.core :as secretary :include-macros true :refer [defroute]])
  (:require-macros [cemerick.cljs.test :refer [deftest is are testing]]))

(defroute "/" [] 1)

(defroute "/users" [] "users")

(defroute "/users/:id" [id] id)

(defroute "/users/:id/food/:food" {:keys [id food]}
  (str id food))

(defroute "/search-1" {:as params}
  params)

(defroute "/search-2" {:keys [query-params]}
  query-params)

(defroute food-route "/food/:food" {:keys [food]}
  (str "food-" food))

(deftest route-test 
  (testing "dispatch!"
    (is (= (secretary/dispatch! "/")
           1))
    (is (= (secretary/dispatch! "/users")
           "users"))
    (is (= (secretary/dispatch! "/users/1")
           "1"))
    (is (= (secretary/dispatch! "/users/kevin/food/bacon")
           "kevinbacon")))

  (testing "named routes"
    (is (fn? food-route))
    (is (fn? (defroute "pickles" {})))
    (is (= (food-route {:food "biscuits"})
           "/food/biscuits")))

  (testing "query-params"
    (is (not (contains? (secretary/dispatch! "/search-1")
                        :query-params)))
    (is (contains? (secretary/dispatch! "/search-1?foo=bar")
                   :query-params))

    (let [s "abc123 !@#$%^&*"
          [p1 p2] (take 2 (iterate #(apply str (shuffle %)) s))
          r (str "/search-2?"
                 "foo=" (js/encodeURIComponent p1)
                 "&bar=" (js/encodeURIComponent p2))]
      (is (= (secretary/dispatch! r)
             {"foo" p1 "bar" p2})))))

(deftest encode-query-params-test
  (testing "handles query params"
    (let [query-params {"id" "kevin" "food" "bacon"}
          encoded (secretary/encode-query-params query-params)]
      (is (= (secretary/decode-query-params encoded)
             query-params)))))

(deftest render-route-test
  (testing "interpolates correctly"
    (is (= (secretary/render-route "/")
           "/"))
    (is (= (secretary/render-route "/users/:id" {:id 1})
           "/users/1"))
    (is (= (secretary/render-route "/users/:id/food/:food" {:id "kevin" :food "bacon"})
           "/users/kevin/food/bacon"))
    (is (= (secretary/render-route "/users/:id" {:id 123})
           "/users/123"))
    (is (= (secretary/render-route "/users/:id" {:id 123 :query-params {:page 2 :per-page 10}})
           "/users/123?page=2&per-page=10"))
    (is (= (secretary/render-route "/users/:id" {:id 123} {:query-params {:page 2 :per-page 10}})
           "/users/123?page=2&per-page=10")))

  (testing "it adds prefixes"
    (binding [secretary/*config* (atom {:prefix "#"})]
      (is (= (secretary/render-route "/users/:id" {:id 1})
             "#/users/1"))))
  
  (testing "leaves param in string if not in map"
    (is (= (secretary/render-route "/users/:id" {})
           "/users/:id"))))
