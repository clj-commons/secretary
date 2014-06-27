(ns secretary.test.core
  (:require [cemerick.cljs.test]
            [secretary.core :as secretary :include-macros true :refer [defroute]])
  (:require-macros [cemerick.cljs.test :refer [deftest is are testing]]))


(deftest query-params-test
  (testing "encodes query params"
    (let [params {:id "kevin" :food "bacon"}
          encoded (secretary/encode-query-params params)]
      (is (= (secretary/decode-query-params encoded)
             params)))

    (are [x y] (= (secretary/encode-query-params x) y)
      {:x [1 2]} "x[]=1&x[]=2"
      {:a [{:b 1} {:b 2}]} "a[0][b]=1&a[1][b]=2"
      {:a [{:b [1 2]} {:b [3 4]}]} "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4"))

  (testing "decodes query params"
    (let [query-string "id=kevin&food=bacong"
          decoded (secretary/decode-query-params query-string)
          encoded (secretary/encode-query-params decoded)]
      (is (re-find #"id=kevin" query-string))
      (is (re-find #"food=bacon" query-string)))

    (are [x y] (= (secretary/decode-query-params x) y)
      "x[]=1&x[]=2" {:x ["1" "2"]}
      "a[0][b]=1&a[1][b]=2" {:a [{:b "1"} {:b "2"}]}
      "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4" {:a [{:b ["1" "2"]} {:b ["3" "4"]}]})))

(deftest route-matches-test
  (testing "non-encoded-routes"
    (is (not (secretary/route-matches "/foo bar baz" "/foo%20bar%20baz")))
    (is (not (secretary/route-matches "/:x" "/,")))
    (is (not (secretary/route-matches "/:x" "/;")))

    (is (= (secretary/route-matches "/:x" "/%2C")
           {:x ","}))

    (is (= (secretary/route-matches "/:x" "/%3B")
           {:x ";"})))

  (testing "utf-8 routes"
    (is (= (secretary/route-matches "/:x" "/%E3%81%8A%E3%81%AF%E3%82%88%E3%81%86")
           {:x "おはよう"})))

  (testing "regex-routes"
    (secretary/reset-routes!)

    (is (= (secretary/route-matches #"/([a-z]+)/(\d+)" "/lol/420")
           ["lol" "420"]))
    (is (not (secretary/route-matches #"/([a-z]+)/(\d+)" "/0x0A/0x0B"))))

  (testing "vector routes"
    (is (= (secretary/route-matches ["/:foo", :foo #"[0-9]+"] "/12345") {:foo "12345"}))
    (is (not (secretary/route-matches ["/:foo", :foo #"[0-9]+"] "/haiii"))))

  (testing "splats"
    (is (= (secretary/route-matches "*" "")
           {:* ""}))
    (is (= (secretary/route-matches "*" "/foo/bar")
           {:* "/foo/bar"}))
    (is (= (secretary/route-matches "*.*" "cat.bat")
           {:* ["cat" "bat"]}))
    (is (= (secretary/route-matches "*path/:file.:ext" "/loller/skates/xxx.zip")
           {:path "/loller/skates"
            :file "xxx"
            :ext "zip"}))
    (is (= (secretary/route-matches "/*a/*b/*c" "/lol/123/abc/look/at/me")
           {:a "lol"
            :b "123"
            :c "abc/look/at/me"}))))


(deftest render-route-test
  (testing "it interpolates correctly"
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
    (is (= (secretary/render-route "/:id/:id" {:id 1})
           "/1/1"))
    (is (= (secretary/render-route "/:id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (secretary/render-route "/*id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (secretary/render-route "/*x/*y" {:x "lmao/rofl/gtfo"
                                             :y "k/thx/bai"})
           "/lmao/rofl/gtfo/k/thx/bai"))
    (is (= (secretary/render-route "/*.:format" {:* "blood"
                                                 :format "tarzan"})
           "/blood.tarzan"))
    (is (= (secretary/render-route "/*.*" {:* ["stab" "wound"]})
           "/stab.wound"))
    (is (= (secretary/render-route ["/:foo", :foo #"[0-9]+"] {:foo "12345"}) "/12345"))
    (is (thrown? ExceptionInfo (secretary/render-route ["/:foo", :foo #"[0-9]+"] {:foo "haiii"}))))

  (testing "it encodes replacements"
    (is (= (secretary/render-route "/users/:path" {:path "yay/for/me"}))
        "/users/yay/for/me")
    (is (= (secretary/render-route "/users/:email" {:email "fake@example.com"}))
        "/users/fake%40example.com"))

  (testing "it adds prefixes"
    (binding [secretary/*config* (atom {:prefix "#"})]
      (is (= (secretary/render-route "/users/:id" {:id 1})
             "#/users/1"))))

  (testing "it leaves param in string if not in map"
    (is (= (secretary/render-route "/users/:id" {})
           "/users/:id"))))


(deftest defroute-test
  (testing "dispatch! with basic routes"
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

  (testing "dispatch! with query-params"
    (secretary/reset-routes!)
    (defroute "/search-1" {:as params} params)

    (is (not (contains? (secretary/dispatch! "/search-1")
                        :query-params)))

    (is (contains? (secretary/dispatch! "/search-1?foo=bar")
                   :query-params))

    (defroute "/search-2" [query-params] query-params)

    (let [s "abc123 !@#$%^&*"
          [p1 p2] (take 2 (iterate #(apply str (shuffle %)) s))
          r (str "/search-2?"
                 "foo=" (js/encodeURIComponent p1)
                 "&bar=" (js/encodeURIComponent p2))]
      (is (= (secretary/dispatch! r)
             {:foo p1 :bar p2})))

    (defroute #"/([a-z]+)/search" [letters {:keys [query-params]}]
      [letters query-params])

    (is (= (secretary/dispatch! "/abc/search")
           ["abc" nil]))

    (is (= (secretary/dispatch! "/abc/search?flavor=pineapple&walnuts=true")
           ["abc" {:flavor "pineapple" :walnuts "true"}])))

  (testing "dispatch! with regex routes"
    (secretary/reset-routes!)
    (defroute #"/([a-z]+)/(\d+)" [letters digits] [letters digits])

    (is (= (secretary/dispatch! "/xyz/123")
           ["xyz" "123"])))

  (testing "dispatch! with vector routes"
    (secretary/reset-routes!)
    (defroute ["/:num/socks" :num #"[0-9]+"] {:keys [num]} (str num"socks"))

    (is (= (secretary/dispatch! "/bacon/socks") nil))
    (is (= (secretary/dispatch! "/123/socks") "123socks")))

  (testing "dispatch! with named-routes and configured prefix"
    (secretary/reset-routes!)

    (binding [secretary/*config* (atom {:prefix "#"})]
      (defroute root-route "/" [] "BAM!")
      (defroute users-route "/users" [] "ZAP!")
      (defroute user-route "/users/:id" {:as params} params)

      (is (= (secretary/dispatch! (root-route))
             "BAM!"))
      (is (= (secretary/dispatch! (users-route))
             "ZAP!"))
      (is (= (secretary/dispatch! (user-route {:id "2"}))
           {:id "2"}))))

  (testing "named routes"
    (secretary/reset-routes!)

    (defroute food-path "/food/:food" [food])
    (defroute search-path "/search" [query-params])

    (is (fn? food-path))
    (is (fn? (defroute "/pickles" {})))
    (is (= (food-path {:food "biscuits"})
           "/food/biscuits"))

    (let [url (search-path {:query-params {:burritos 10, :tacos 200}})]
      (is (re-find #"burritos=10" url))
      (is (re-find #"tacos=200" url)))))

(deftest locate-route
  (testing "locate-route includes original route as last value in return vector"
    (secretary/reset-routes!)

    (defroute "/my-route/:some-param" [params])
    (defroute #"my-regexp-route-[a-zA-Z]*" [params])
    (defroute ["/my-vector-route/:some-param", :some-param #"[0-9]+"] [params])

    (is (= "/my-route/:some-param" (secretary/locate-route-value "/my-route/100")))
    ;; is this right? shouldn't this just return nil?
    (is (thrown? js/Error (secretary/locate-route-value "/not-a-route")))

    (let [[route & validations] (secretary/locate-route-value "/my-vector-route/100")
          {:keys [some-param]} (apply hash-map validations)]
      (is (= "/my-vector-route/:some-param" route))
      (is (= (.-source #"[0-9]+")
             (.-source some-param))))
    (is (thrown? js/Error (secretary/locate-route-value "/my-vector-route/foo")))

    (is (= (.-source #"my-regexp-route-[a-zA-Z]*")
           (.-source (secretary/locate-route-value "my-regexp-route-test"))))))
