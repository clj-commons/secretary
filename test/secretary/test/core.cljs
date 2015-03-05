(ns secretary.test.core
  (:require [cemerick.cljs.test :as test]
            [secretary.core :as sec])
  ;; To :refer all of the used functions from secretary, replace above spec with
  ;;[secretary.core :refer [encode-query-params decode-query-params route-matches
  ;;     render-route reset-routes! dispatch! *config* locate-route-value]
  (:require-macros [cemerick.cljs.test :refer [deftest is are testing]]
                   [secretary.core :refer [defroute]]))


(deftest query-params-test
  (testing "encodes query params"
    (let [params {:id "kevin" :food "bacon"}
          encoded (sec/encode-query-params params)]
      (is (= (sec/decode-query-params encoded)
             params)))

    (are [x y] (= (sec/encode-query-params x) y)
      {:x [1 2]} "x[]=1&x[]=2"
      {:a [{:b 1} {:b 2}]} "a[0][b]=1&a[1][b]=2"
      {:a [{:b [1 2]} {:b [3 4]}]} "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4"))

  (testing "decodes query params"
    (let [query-string "id=kevin&food=bacong"
          decoded (sec/decode-query-params query-string)
          encoded (sec/encode-query-params decoded)]
      (is (re-find #"id=kevin" query-string))
      (is (re-find #"food=bacon" query-string)))

    (are [x y] (= (sec/decode-query-params x) y)
      "x[]=1&x[]=2" {:x ["1" "2"]}
      "a[0][b]=1&a[1][b]=2" {:a [{:b "1"} {:b "2"}]}
      "a[0][b][]=1&a[0][b][]=2&a[1][b][]=3&a[1][b][]=4" {:a [{:b ["1" "2"]} {:b ["3" "4"]}]})))

(deftest route-matches-test
  (testing "non-encoded-routes"
    (is (not (sec/route-matches "/foo bar baz" "/foo%20bar%20baz")))
    (is (not (sec/route-matches "/:x" "/,")))
    (is (not (sec/route-matches "/:x" "/;")))

    (is (= (sec/route-matches "/:x" "/%2C")
           {:x ","}))

    (is (= (sec/route-matches "/:x" "/%3B")
           {:x ";"})))

  (testing "utf-8 routes"
    (is (= (sec/route-matches "/:x" "/%E3%81%8A%E3%81%AF%E3%82%88%E3%81%86")
           {:x "おはよう"})))

  (testing "regex-routes"
    (sec/reset-routes!)

    (is (= (sec/route-matches #"/([a-z]+)/(\d+)" "/lol/420")
           ["lol" "420"]))
    (is (not (sec/route-matches #"/([a-z]+)/(\d+)" "/0x0A/0x0B"))))

  (testing "vector routes"
    (is (= (sec/route-matches ["/:foo", :foo #"[0-9]+"] "/12345") {:foo "12345"}))
    (is (not (sec/route-matches ["/:foo", :foo #"[0-9]+"] "/haiii"))))

  (testing "splats"
    (is (= (sec/route-matches "*" "")
           {:* ""}))
    (is (= (sec/route-matches "*" "/foo/bar")
           {:* "/foo/bar"}))
    (is (= (sec/route-matches "*.*" "cat.bat")
           {:* ["cat" "bat"]}))
    (is (= (sec/route-matches "*path/:file.:ext" "/loller/skates/xxx.zip")
           {:path "/loller/skates"
            :file "xxx"
            :ext "zip"}))
    (is (= (sec/route-matches "/*a/*b/*c" "/lol/123/abc/look/at/me")
           {:a "lol"
            :b "123"
            :c "abc/look/at/me"}))))


(deftest render-route-test
  (testing "it interpolates correctly"
    (is (= (sec/render-route "/")
           "/"))
    (is (= (sec/render-route "/users/:id" {:id 1})
           "/users/1"))
    (is (= (sec/render-route "/users/:id/food/:food" {:id "kevin" :food "bacon"})
           "/users/kevin/food/bacon"))
    (is (= (sec/render-route "/users/:id" {:id 123})
           "/users/123"))
    (is (= (sec/render-route "/users/:id" {:id 123 :query-params {:page 2 :per-page 10}})
           "/users/123?page=2&per-page=10"))
    (is (= (sec/render-route "/:id/:id" {:id 1})
           "/1/1"))
    (is (= (sec/render-route "/:id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (sec/render-route "/*id/:id" {:id [1 2]})
           "/1/2"))
    (is (= (sec/render-route "/*x/*y" {:x "lmao/rofl/gtfo"
                                             :y "k/thx/bai"})
           "/lmao/rofl/gtfo/k/thx/bai"))
    (is (= (sec/render-route "/*.:format" {:* "blood"
                                                 :format "tarzan"})
           "/blood.tarzan"))
    (is (= (sec/render-route "/*.*" {:* ["stab" "wound"]})
           "/stab.wound"))
    (is (= (sec/render-route ["/:foo", :foo #"[0-9]+"] {:foo "12345"}) "/12345"))
    (is (thrown? ExceptionInfo (sec/render-route ["/:foo", :foo #"[0-9]+"] {:foo "haiii"}))))

  (testing "it encodes replacements"
    (is (= (sec/render-route "/users/:path" {:path "yay/for/me"}))
        "/users/yay/for/me")
    (is (= (sec/render-route "/users/:email" {:email "fake@example.com"}))
        "/users/fake%40example.com"))

  (testing "it adds prefixes"
    (binding [sec/*config* (atom {:prefix "#"})]
      (is (= (sec/render-route "/users/:id" {:id 1})
             "#/users/1"))))

  (testing "it leaves param in string if not in map"
    (is (= (sec/render-route "/users/:id" {})
           "/users/:id"))))


(deftest defroute-test
  (testing "dispatch! with basic routes"
    (sec/reset-routes!)

    (defroute "/" [] "BAM!")
    (defroute "/users" [] "ZAP!")
    (defroute "/users/:id" {:as params} params)
    (defroute "/users/:id/food/:food" {:as params} params)

    (is (= (sec/dispatch! "/")
           "BAM!"))
    (is (= (sec/dispatch! "")
           "BAM!"))
    (is (= (sec/dispatch! "/users")
           "ZAP!"))
    (is (= (sec/dispatch! "/users/1")
           {:id "1"}))
    (is (= (sec/dispatch! "/users/kevin/food/bacon")
           {:id "kevin", :food "bacon"})))

  (testing "dispatch! with query-params"
    (sec/reset-routes!)
    (defroute "/search-1" {:as params} params)

    (is (not (contains? (sec/dispatch! "/search-1")
                        :query-params)))

    (is (contains? (sec/dispatch! "/search-1?foo=bar")
                   :query-params))

    (defroute "/search-2" [query-params] query-params)

    (let [s "abc123 !@#$%^&*"
          [p1 p2] (take 2 (iterate #(apply str (shuffle %)) s))
          r (str "/search-2?"
                 "foo=" (js/encodeURIComponent p1)
                 "&bar=" (js/encodeURIComponent p2))]
      (is (= (sec/dispatch! r)
             {:foo p1 :bar p2})))

    (defroute #"/([a-z]+)/search" [letters {:keys [query-params]}]
      [letters query-params])

    (is (= (sec/dispatch! "/abc/search")
           ["abc" nil]))

    (is (= (sec/dispatch! "/abc/search?flavor=pineapple&walnuts=true")
           ["abc" {:flavor "pineapple" :walnuts "true"}])))

  (testing "sec/dispatch! with regex routes"
    (sec/reset-routes!)
    (defroute #"/([a-z]+)/(\d+)" [letters digits] [letters digits])

    (is (= (sec/dispatch! "/xyz/123")
           ["xyz" "123"])))

  (testing "sec/dispatch! with vector routes"
    (sec/reset-routes!)
    (defroute ["/:num/socks" :num #"[0-9]+"] {:keys [num]} (str num"socks"))

    (is (= (sec/dispatch! "/bacon/socks") nil))
    (is (= (sec/dispatch! "/123/socks") "123socks")))

  (testing "dispatch! with named-routes and configured prefix"
    (sec/reset-routes!)

    (binding [sec/*config* (atom {:prefix "#"})]
      (defroute root-route "/" [] "BAM!")
      (defroute users-route "/users" [] "ZAP!")
      (defroute user-route "/users/:id" {:as params} params)

      (is (= (sec/dispatch! (root-route))
             "BAM!"))
      (is (= (sec/dispatch! (users-route))
             "ZAP!"))
      (is (= (sec/dispatch! (user-route {:id "2"}))
           {:id "2"}))))

  (testing "named routes"
    (sec/reset-routes!)

    (defroute food-path "/food/:food" [food])
    (defroute search-path "/search" [query-params])

    (is (fn? food-path))
    (is (fn? (defroute "/pickles" {})))
    (is (= (food-path {:food "biscuits"})
           "/food/biscuits"))

    (let [url (search-path {:query-params {:burritos 10, :tacos 200}})]
      (is (re-find #"burritos=10" url))
      (is (re-find #"tacos=200" url))))
  
  (testing "dispatch! with splat and no home route"
    (sec/reset-routes!)

    (defroute "/users/:id" {:as params} params)
    (defroute "*" [] "SPLAT")

    (is (= (sec/dispatch! "/users/1")
           {:id "1"}))
    (is (= (sec/dispatch! "")
           "SPLAT"))
    (is (= (sec/dispatch! "/users")
           "SPLAT"))))

(deftest locate-route
  (testing "locate-route includes original route as last value in return vector"
    (sec/reset-routes!)

    (defroute "/my-route/:some-param" [params])
    (defroute #"my-regexp-route-[a-zA-Z]*" [params])
    (defroute ["/my-vector-route/:some-param", :some-param #"[0-9]+"] [params])

    (is (= "/my-route/:some-param" (sec/locate-route-value "/my-route/100")))
    ;; is this right? shouldn't this just return nil?
    (is (thrown? js/Error (sec/locate-route-value "/not-a-route")))

    (let [[route & validations] (sec/locate-route-value "/my-vector-route/100")
          {:keys [some-param]} (apply hash-map validations)]
      (is (= "/my-vector-route/:some-param" route))
      (is (= (.-source #"[0-9]+")
             (.-source some-param))))
    (is (thrown? js/Error (sec/locate-route-value "/my-vector-route/foo")))

    (is (= (.-source #"my-regexp-route-[a-zA-Z]*")
           (.-source (sec/locate-route-value "my-regexp-route-test"))))))
