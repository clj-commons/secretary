(ns secretary.test.core
  (:require [cemerick.cljs.test :as test]
            [secretary.core :as secretary ;;:include-macros true :refer [defroute]
             ])
  ;;(:require-macros [cemerick.cljs.test :refer [deftest is are testing]])
  )


(test/deftest query-params-test
  (test/testing "encodes query params"
    (let [params {:id "kevin" :food "bacon"}
          encoded (secretary/encode-query-params params)]
      (test/is (= (secretary/decode-query-params encoded)
             params)))

    (test/are [x y] (= (secretary/encode-query-params x) y)
      {:x [1 2]} "x[]=1&x[]=2"
      {:a [{:b 1} {:b 2}]} "a[0][b]=1&a[1][b]=2"
      {:a [{:b [1 2]} {:b [3 4]}]} "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4"))

  (test/testing "decodes query params"
    (let [query-string "id=kevin&food=bacong"
          decoded (secretary/decode-query-params query-string)
          encoded (secretary/encode-query-params decoded)]
      (test/is (re-find #"id=kevin" query-string))
      (test/is (re-find #"food=bacon" query-string)))

    (test/are [x y] (= (secretary/decode-query-params x) y)
      "x[]=1&x[]=2" {:x ["1" "2"]}
      "a[0][b]=1&a[1][b]=2" {:a [{:b "1"} {:b "2"}]}
      "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4" {:a [{:b ["1" "2"]} {:b ["3" "4"]}]})))

(test/deftest route-matches-test
  (test/testing "non-encoded-routes"
    (test/is (not (secretary/route-matches "/foo bar baz" "/foo%20bar%20baz")))
    (test/is (not (secretary/route-matches "/:x" "/,")))
    (test/is (not (secretary/route-matches "/:x" "/;")))

    (test/is (= (secretary/route-matches "/:x" "/%2C")
           {:x ","}))

    (test/is (= (secretary/route-matches "/:x" "/%3B")
           {:x ";"})))

  (test/testing "utf-8 routes"
    (test/is (= (secretary/route-matches "/:x" "/%E3%81%8A%E3%81%AF%E3%82%88%E3%81%86")
           {:x "おはよう"})))

  (test/testing "regex-routes"
    (secretary/reset-routes!)

    (test/is (= (secretary/route-matches #"/([a-z]+)/(\d+)" "/lol/420")
           ["lol" "420"]))
    (test/is (not (secretary/route-matches #"/([a-z]+)/(\d+)" "/0x0A/0x0B"))))

  (test/testing "vector routes"
    (test/is (= (secretary/route-matches ["/:foo", :foo #"[0-9]+"] "/12345") {:foo "12345"}))
    (test/is (not (secretary/route-matches ["/:foo", :foo #"[0-9]+"] "/haiii"))))

  (test/testing "splats"
    (test/is (= (secretary/route-matches "*" "")
           {:* ""}))
    (test/is (= (secretary/route-matches "*" "/foo/bar")
           {:* "/foo/bar"}))
    (test/is (= (secretary/route-matches "*.*" "cat.bat")
           {:* ["cat" "bat"]}))
    (test/is (= (secretary/route-matches "*path/:file.:ext" "/loller/skates/xxx.zip")
           {:path "/loller/skates"
            :file "xxx"
            :ext "zip"}))
    (test/is (= (secretary/route-matches "/*a/*b/*c" "/lol/123/abc/look/at/me")
           {:a "lol"
            :b "123"
            :c "abc/look/at/me"}))))


(test/deftest render-route-test
  (test/testing "it interpolates correctly"
    (test/is (= (secretary/render-route "/")
           "/"))
    (test/is (= (secretary/render-route "/users/:id" {:id 1})
           "/users/1"))
    (test/is (= (secretary/render-route "/users/:id/food/:food" {:id "kevin" :food "bacon"})
           "/users/kevin/food/bacon"))
    (test/is (= (secretary/render-route "/users/:id" {:id 123})
           "/users/123"))
    (test/is (= (secretary/render-route "/users/:id" {:id 123 :query-params {:page 2 :per-page 10}})
           "/users/123?page=2&per-page=10"))
    (test/is (= (secretary/render-route "/:id/:id" {:id 1})
           "/1/1"))
    (test/is (= (secretary/render-route "/:id/:id" {:id [1 2]})
           "/1/2"))
    (test/is (= (secretary/render-route "/*id/:id" {:id [1 2]})
           "/1/2"))
    (test/is (= (secretary/render-route "/*x/*y" {:x "lmao/rofl/gtfo"
                                             :y "k/thx/bai"})
           "/lmao/rofl/gtfo/k/thx/bai"))
    (test/is (= (secretary/render-route "/*.:format" {:* "blood"
                                                 :format "tarzan"})
           "/blood.tarzan"))
    (test/is (= (secretary/render-route "/*.*" {:* ["stab" "wound"]})
           "/stab.wound"))
    (test/is (= (secretary/render-route ["/:foo", :foo #"[0-9]+"] {:foo "12345"}) "/12345"))
    (test/is (thrown? ExceptionInfo (secretary/render-route ["/:foo", :foo #"[0-9]+"] {:foo "haiii"}))))

  (test/testing "it encodes replacements"
    (test/is (= (secretary/render-route "/users/:path" {:path "yay/for/me"}))
        "/users/yay/for/me")
    (test/is (= (secretary/render-route "/users/:email" {:email "fake@example.com"}))
        "/users/fake%40example.com"))

  (test/testing "it adds prefixes"
    (binding [secretary/*config* (atom {:prefix "#"})]
      (test/is (= (secretary/render-route "/users/:id" {:id 1})
             "#/users/1"))))

  (test/testing "it leaves param in string if not in map"
    (test/is (= (secretary/render-route "/users/:id" {})
           "/users/:id"))))


(test/deftest defroute-test
  (test/testing "dispatch! with basic routes"
    (secretary/reset-routes!)

    (secretary/defroute "/" [] "BAM!")
    (secretary/defroute "/users" [] "ZAP!")
    (secretary/defroute "/users/:id" {:as params} params)
    (secretary/defroute "/users/:id/food/:food" {:as params} params)

    (test/is (= (secretary/dispatch! "/")
           "BAM!"))
    (test/is (= (secretary/dispatch! "")
           "BAM!"))
    (test/is (= (secretary/dispatch! "/users")
           "ZAP!"))
    (test/is (= (secretary/dispatch! "/users/1")
           {:id "1"}))
    (test/is (= (secretary/dispatch! "/users/kevin/food/bacon")
           {:id "kevin", :food "bacon"})))

  (test/testing "dispatch! with query-params"
    (secretary/reset-routes!)
    (secretary/defroute "/search-1" {:as params} params)

    (test/is (not (contains? (secretary/dispatch! "/search-1")
                        :query-params)))

    (test/is (contains? (secretary/dispatch! "/search-1?foo=bar")
                   :query-params))

    (secretary/defroute "/search-2" [query-params] query-params)

    (let [s "abc123 !@#$%^&*"
          [p1 p2] (take 2 (iterate #(apply str (shuffle %)) s))
          r (str "/search-2?"
                 "foo=" (js/encodeURIComponent p1)
                 "&bar=" (js/encodeURIComponent p2))]
      (test/is (= (secretary/dispatch! r)
             {:foo p1 :bar p2})))

    (secretary/defroute #"/([a-z]+)/search" [letters {:keys [query-params]}]
      [letters query-params])

    (test/is (= (secretary/dispatch! "/abc/search")
           ["abc" nil]))

    (test/is (= (secretary/dispatch! "/abc/search?flavor=pineapple&walnuts=true")
           ["abc" {:flavor "pineapple" :walnuts "true"}])))

  (test/testing "dispatch! with regex routes"
    (secretary/reset-routes!)
    (secretary/defroute #"/([a-z]+)/(\d+)" [letters digits] [letters digits])

    (test/is (= (secretary/dispatch! "/xyz/123")
           ["xyz" "123"])))

  (test/testing "dispatch! with vector routes"
    (secretary/reset-routes!)
    (secretary/defroute ["/:num/socks" :num #"[0-9]+"] {:keys [num]} (str num"socks"))

    (test/is (= (secretary/dispatch! "/bacon/socks") nil))
    (test/is (= (secretary/dispatch! "/123/socks") "123socks")))

  (test/testing "dispatch! with named-routes and configured prefix"
    (secretary/reset-routes!)

    (binding [secretary/*config* (atom {:prefix "#"})]
      (secretary/defroute root-route "/" [] "BAM!")
      (secretary/defroute users-route "/users" [] "ZAP!")
      (secretary/defroute user-route "/users/:id" {:as params} params)

      (test/is (= (secretary/dispatch! (root-route))
             "BAM!"))
      (test/is (= (secretary/dispatch! (users-route))
             "ZAP!"))
      (test/is (= (secretary/dispatch! (user-route {:id "2"}))
           {:id "2"}))))

  (test/testing "named routes"
    (secretary/reset-routes!)

    (secretary/defroute food-path "/food/:food" [food])
    (secretary/defroute search-path "/search" [query-params])

    (test/is (fn? food-path))
    (test/is (fn? (secretary/defroute "/pickles" {})))
    (test/is (= (food-path {:food "biscuits"})
           "/food/biscuits"))

    (let [url (search-path {:query-params {:burritos 10, :tacos 200}})]
      (test/is (re-find #"burritos=10" url))
      (test/is (re-find #"tacos=200" url))))
  
  (test/testing "dispatch! with splat and no home route"
    (secretary/reset-routes!)

    (secretary/defroute "/users/:id" {:as params} params)
    (secretary/defroute "*" [] "SPLAT")

    (test/is (= (secretary/dispatch! "/users/1")
           {:id "1"}))
    (test/is (= (secretary/dispatch! "")
           "SPLAT"))
    (test/is (= (secretary/dispatch! "/users")
           "SPLAT"))))

(test/deftest locate-route
  (test/testing "locate-route includes original route as last value in return vector"
    (secretary/reset-routes!)

    (secretary/defroute "/my-route/:some-param" [params])
    (secretary/defroute #"my-regexp-route-[a-zA-Z]*" [params])
    (secretary/defroute ["/my-vector-route/:some-param", :some-param #"[0-9]+"] [params])

    (test/is (= "/my-route/:some-param" (secretary/locate-route-value "/my-route/100")))
    ;; is this right? shouldn't this just return nil?
    (test/is (thrown? js/Error (secretary/locate-route-value "/not-a-route")))

    (let [[route & validations] (secretary/locate-route-value "/my-vector-route/100")
          {:keys [some-param]} (apply hash-map validations)]
      (test/is (= "/my-vector-route/:some-param" route))
      (test/is (= (.-source #"[0-9]+")
             (.-source some-param))))
    (test/is (thrown? js/Error (secretary/locate-route-value "/my-vector-route/foo")))

    (test/is (= (.-source #"my-regexp-route-[a-zA-Z]*")
           (.-source (secretary/locate-route-value "my-regexp-route-test"))))))
