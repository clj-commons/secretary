(ns secretary.test.core
  (:require [cemerick.cljs.test :as t]
            [secretary.core :as secretary :include-macros true :refer [defroute]])
  (:require-macros [cemerick.cljs.test :refer [deftest is are testing]]))

;; Test helpers

(defn compile-and-match [r t]
  (-> (secretary/compile-route r)
      (secretary/route-matches t)))

;; Tests

(deftest route-test 
  (is (= 1 1))
  (testing "dispatch!"
    (secretary/reset-routes!)
    (defroute "/" [] "BAM!")
    (defroute "/users" [] "ZAP!")
    (defroute "/users/:id" {:as params} params)
    (defroute "/users/:id/food/:food" {:as params} params)

    (is (= (secretary/dispatch! "/")
           "BAM!"))
    (is (= (secretary/dispatch! "/users")
           "ZAP!"))
    (is (= (secretary/dispatch! "/users/1")
           {:id "1"}))
    (is (= (secretary/dispatch! "/users/kevin/food/bacon")
           {:id "kevin", :food "bacon"})))

  (testing "named routes"
    (secretary/reset-routes!)

    (defroute food-route "/food/:food" {:keys [food]})

    (is (fn? food-route))
    (is (fn? (defroute "/pickles" {})))
    (is (= (food-route {:food "biscuits"})
           "/food/biscuits")))

  (testing "query-params"
    (secretary/reset-routes!)
    (defroute "/search-1" {:as params} params)
    (defroute "/search-2" [query-params] query-params)

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
             {"foo" p1 "bar" p2}))))

  (testing "non-encoded-routes"
    (is (compile-and-match "/foo bar baz" "/foo%20bar%20baz")))

  (testing "utf-8 routes"
    (is (= (compile-and-match "/:x" "/%E3%81%8A%E3%81%AF%E3%82%88%E3%81%86")
           {:x "おはよう"})))

  (testing "splats"
    (is (= (compile-and-match "*" "")
           {:* ""}))
    (is (= (compile-and-match "*" "/foo/bar")
           {:* "/foo/bar"}))
    (is (= (compile-and-match "*.*" "cat.bat")
           {:* ["cat" "bat"]}))
    (is (= (compile-and-match "*path/:file.:ext" "/loller/skates/xxx.zip")
           {:path "/loller/skates"
            :file "xxx"
            :ext "zip"}))
    (is (= (compile-and-match "/*a/*b/*c" "/lol/123/abc/look/at/me")
           {:a "lol"
            :b "123"
            :c "abc/look/at/me"}))))

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
    (is (= (secretary/render-route "/:id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (secretary/render-route "/*id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (secretary/render-route "/*x/*y" {:x "lmao/rofl/gtfo"
                                             :y "k/thx/bai"})
           "/lmao/rofl/gtfo/k/thx/bai"))
    (is (= (secretary/render-route "/*.:format" {:* "blood"
                                                 :format "tarzan"})))
    (is (= (secretary/render-route "/*.*" {:* ["stab" "wound"]}))))

  (testing "it adds prefixes"
    (binding [secretary/*config* (atom {:prefix "#"})]
      (is (= (secretary/render-route "/users/:id" {:id 1})
             "#/users/1"))))
  
  (testing "leaves param in string if not in map"
    (is (= (secretary/render-route "/users/:id" {})
           "/users/:id"))))
