(ns secretary.test.core
  (:require
   [cemerick.cljs.test :as t]
   [secretary.core :as s])
  (:require-macros
   [cemerick.cljs.test :refer [deftest is are testing]]))


(deftest query-params-test
  (testing "encodes query params"
    (let [params {:id "kevin" :food "bacon"}
          encoded (s/encode-query-params params)]
      (is (= (s/decode-query-params encoded)
             params)))

    (are [x y] (= (s/encode-query-params x) y)
      {:x [1 2]} "x[]=1&x[]=2"
      {:a [{:b 1} {:b 2}]} "a[0][b]=1&a[1][b]=2"
      {:a [{:b [1 2]} {:b [3 4]}]} "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4"))

  (testing "decodes query params"
    (let [query-string "id=kevin&food=bacong"
          decoded (s/decode-query-params query-string)
          encoded (s/encode-query-params decoded)]
      (is (re-find #"id=kevin" query-string))
      (is (re-find #"food=bacon" query-string)))

    (are [x y] (= (s/decode-query-params x) y)
      "x[]=1&x[]=2" {:x ["1" "2"]}
      "a[0][b]=1&a[1][b]=2" {:a [{:b "1"} {:b "2"}]}
      "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4" {:a [{:b ["1" "2"]} {:b ["3" "4"]}]})))

(deftest route-matches-test
  (testing "non-encoded-routes"
    (is (not (s/route-matches "/foo bar baz" "/foo%20bar%20baz")))
    (is (not (s/route-matches "/:x" "/,")))
    (is (not (s/route-matches "/:x" "/;")))

    (is (= (s/route-matches "/:x" "/%2C")
           {:x ","}))

    (is (= (s/route-matches "/:x" "/%3B")
           {:x ";"})))

  (testing "utf-8 routes"
    (is (= (s/route-matches "/:x" "/%E3%81%8A%E3%81%AF%E3%82%88%E3%81%86")
           {:x "おはよう"})))

  (testing "regex-routes"
    (s/reset-routes!)

    (is (= (s/route-matches #"/([a-z]+)/(\d+)" "/lol/420")
           ["lol" "420"]))
    (is (not (s/route-matches #"/([a-z]+)/(\d+)" "/0x0A/0x0B"))))

  (testing "vector routes"
    (is (= (s/route-matches ["/:foo", :foo #"[0-9]+"] "/12345") {:foo "12345"}))
    (is (not (s/route-matches ["/:foo", :foo #"[0-9]+"] "/haiii"))))

  (testing "splats"
    (is (= (s/route-matches "*" "")
           {:* ""}))
    (is (= (s/route-matches "*" "/foo/bar")
           {:* "/foo/bar"}))
    (is (= (s/route-matches "*.*" "cat.bat")
           {:* ["cat" "bat"]}))
    (is (= (s/route-matches "*path/:file.:ext" "/loller/skates/xxx.zip")
           {:path "/loller/skates"
            :file "xxx"
            :ext "zip"}))
    (is (= (s/route-matches "/*a/*b/*c" "/lol/123/abc/look/at/me")
           {:a "lol"
            :b "123"
            :c "abc/look/at/me"}))))


(deftest render-route-test
  (testing "it interpolates correctly"
    (is (= (s/render-route "/")
           "/"))
    (is (= (s/render-route "/users/:id" {:id 1})
           "/users/1"))
    (is (= (s/render-route "/users/:id/food/:food" {:id "kevin" :food "bacon"})
           "/users/kevin/food/bacon"))
    (is (= (s/render-route "/users/:id" {:id 123})
           "/users/123"))
    (is (= (s/render-route "/users/:id" {:id 123 :query-params {:page 2 :per-page 10}})
           "/users/123?page=2&per-page=10"))
    (is (= (s/render-route "/:id/:id" {:id 1})
           "/1/1"))
    (is (= (s/render-route "/:id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (s/render-route "/*id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (s/render-route "/*x/*y" {:x "lmao/rofl/gtfo"
                                             :y "k/thx/bai"})
           "/lmao/rofl/gtfo/k/thx/bai"))
    (is (= (s/render-route "/*.:format" {:* "blood"
                                                 :format "tarzan"})
           "/blood.tarzan"))
    (is (= (s/render-route "/*.*" {:* ["stab" "wound"]})
           "/stab.wound"))
    (is (= (s/render-route ["/:foo", :foo #"[0-9]+"] {:foo "12345"}) "/12345"))
    (is (thrown? ExceptionInfo (s/render-route ["/:foo", :foo #"[0-9]+"] {:foo "haiii"}))))

  (testing "it encodes replacements"
    (is (= (s/render-route "/users/:path" {:path "yay/for/me"}))
        "/users/yay/for/me")
    (is (= (s/render-route "/users/:email" {:email "fake@example.com"}))
        "/users/fake%40example.com"))

  (testing "it adds prefixes"
    (binding [s/*config* (atom {:prefix "#"})]
      (is (= (s/render-route "/users/:id" {:id 1})
             "#/users/1"))))

  (testing "it leaves param in string if not in map"
    (is (= (s/render-route "/users/:id" {})
           "/users/:id"))))


(deftest defroute-test
  (testing "dispatch! with basic routes"
    (s/reset-routes!)

    (s/defroute "/" [] "BAM!")
    (s/defroute "/users" [] "ZAP!")
    (s/defroute "/users/:id" {:as params} params)
    (s/defroute "/users/:id/food/:food" {:as params} params)

    (is (= (s/dispatch! "/")
           "BAM!"))
    (is (= (s/dispatch! "")
           "BAM!"))
    (is (= (s/dispatch! "/users")
           "ZAP!"))
    (is (= (s/dispatch! "/users/1")
           {:id "1"}))
    (is (= (s/dispatch! "/users/kevin/food/bacon")
           {:id "kevin", :food "bacon"})))

  (testing "dispatch! with query-params"
    (s/reset-routes!)
    (s/defroute "/search-1" {:as params} params)

    (is (not (contains? (s/dispatch! "/search-1")
                        :query-params)))

    (is (contains? (s/dispatch! "/search-1?foo=bar")
                   :query-params))

    (s/defroute "/search-2" [query-params] query-params)

    (let [s "abc123 !@#$%^&*"
          [p1 p2] (take 2 (iterate #(apply str (shuffle %)) s))
          r (str "/search-2?"
                 "foo=" (js/encodeURIComponent p1)
                 "&bar=" (js/encodeURIComponent p2))]
      (is (= (s/dispatch! r)
             {:foo p1 :bar p2})))

    (s/defroute #"/([a-z]+)/search" {:as params}
      (let [[letters {:keys [query-params]}] params]
        [letters query-params]))

    (is (= (s/dispatch! "/abc/search")
           ["abc" nil]))

    (is (= (s/dispatch! "/abc/search?flavor=pineapple&walnuts=true")
           ["abc" {:flavor "pineapple" :walnuts "true"}])))

  (testing "s/dispatch! with regex routes"
    (s/reset-routes!)
    (s/defroute #"/([a-z]+)/(\d+)" [letters digits] [letters digits])

    (is (= (s/dispatch! "/xyz/123")
           ["xyz" "123"])))

  (testing "s/dispatch! with vector routes"
    (s/reset-routes!)
    (s/defroute ["/:num/socks" :num #"[0-9]+"] {:keys [num]} (str num"socks"))

    (is (= (s/dispatch! "/bacon/socks") nil))
    (is (= (s/dispatch! "/123/socks") "123socks")))

  (testing "dispatch! with named-routes and configured prefix"
    (s/reset-routes!)

    (binding [s/*config* (atom {:prefix "#"})]
      (s/defroute root-route "/" [] "BAM!")
      (s/defroute users-route "/users" [] "ZAP!")
      (s/defroute user-route "/users/:id" {:as params} params)

      (is (= (s/dispatch! (root-route))
             "BAM!"))
      (is (= (s/dispatch! (users-route))
             "ZAP!"))
      (is (= (s/dispatch! (user-route {:id "2"}))
           {:id "2"}))))

  (testing "named routes"
    (s/reset-routes!)

    (s/defroute food-path "/food/:food" [food])
    (s/defroute search-path "/search" [query-params])

    (is (fn? food-path))
    (is (fn? (s/defroute "/pickles" {})))
    (is (= (food-path {:food "biscuits"})
           "/food/biscuits"))

    (let [url (search-path {:query-params {:burritos 10, :tacos 200}})]
      (is (re-find #"burritos=10" url))
      (is (re-find #"tacos=200" url))))
  
  (testing "dispatch! with splat and no home route"
    (s/reset-routes!)

    (s/defroute "/users/:id" {:as params} params)
    (s/defroute "*" [] "SPLAT")

    (is (= (s/dispatch! "/users/1")
           {:id "1"}))
    (is (= (s/dispatch! "")
           "SPLAT"))
    (is (= (s/dispatch! "/users")
           "SPLAT"))))

(deftest locate-route
  (testing "locate-route includes original route as last value in return vector"
    (s/reset-routes!)

    (s/defroute "/my-route/:some-param" [params])
    (s/defroute #"my-regexp-route-[a-zA-Z]*" [params])
    (s/defroute ["/my-vector-route/:some-param", :some-param #"[0-9]+"] [params])

    (is (= "/my-route/:some-param" (s/locate-route-value "/my-route/100")))
    ;; is this right? shouldn't this just return nil?
    (is (thrown? js/Error (s/locate-route-value "/not-a-route")))

    (let [[route & validations] (s/locate-route-value "/my-vector-route/100")
          {:keys [some-param]} (apply hash-map validations)]
      (is (= "/my-vector-route/:some-param" route))
      (is (= (.-source #"[0-9]+")
             (.-source some-param))))
    (is (thrown? js/Error (s/locate-route-value "/my-vector-route/foo")))

    (is (= (.-source #"my-regexp-route-[a-zA-Z]*")
           (.-source (s/locate-route-value "my-regexp-route-test"))))))
