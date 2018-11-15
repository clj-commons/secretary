(ns secretary.test.core
  (:require
   [cljs.test :as t :refer-macros [deftest testing is are]]
   [secretary.core :as s]))

;; ---------------------------------------------------------------------
;; Route matching/rendering testing

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
    (is (= (s/route-matches #"/([a-z]+)/(\d+)" "/lol/420")
           ["lol" "420"]))
    (is (not (s/route-matches #"/([a-z]+)/(\d+)" "/0x0A/0x0B"))))

  (testing "vector routes"
    (is (= (s/route-matches ["/:foo", :foo #"[0-9]+"] "/12345")
           {:foo "12345"}))
    (is (not (s/route-matches ["/:foo", :foo #"[0-9]+"]
                              "/haiii"))))

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

  (testing "it leaves param in string if not in map"
    (is (= (s/render-route "/users/:id" {})
           "/users/:id"))))

(deftest make-route-test
  (testing "make-route compiles string routes"
    (let [s "/a/:x"]
      (is (not= (:pattern (s/make-route s identity))
                s)))
    (is (thrown? js/Error (s/make-route "foo" 10)))))

;; ---------------------------------------------------------------------
;; Dispatch testing

;;; Basic dispatcher

(s/defroute r1 "/" {} "BAM!")
(s/defroute r2 "/users" {} "ZAP!")
(s/defroute r3 "/users/:id" {:as params} params)
(s/defroute r4 "/users/:id/food/:food" {:as params} params)

(def rs [r1 r2 r3 r4])

(defn d1 [uri]
  (let [[r ms] (some (fn [r]
                       (when-let [ms (s/route-matches r uri)]
                         [r ms]))
                     rs)]
    (when r (.action r ms))))

(deftest defroute-test
  (testing "dispatch! with basic routes"
    (is (= (d1 "/")
           "BAM!"))
    (is (= (d1 "/users")
           "ZAP!"))
    (is (= (d1 "/users/1")
           {:id "1"}))
    (is (= (d1 "/users/kevin/food/bacon")
           {:id "kevin", :food "bacon"}))))

;;; URI dispatcher

(s/defroute ur1 "/" {:as req}
  req)

(s/defroute ur2 #"/ur2/([a-z]+)" {[letters] :params qps :query-params :as req}
  [letters qps])

(s/defroute ur3 #"/ur3/([a-z]+)/(\d+)" {[letters digits] :params}
  [letters digits])

(s/defroute ur4 ["/ur4/:num/socks" :num #"[0-9]+"] {{:keys [num]} :params}
  (str num "socks"))

(s/defroute ur5 #"/ur5/(\d+)" [d]
  d)

(def d2
  (s/uri-dispatcher [ur1 ur2 ur3 ur4 ur5]))

(deftest uri-dispatcher-test
  (testing "uri-dispatcher (string routes)"
    (is (contains? (d2 "/") :query-params))

    (is (nil? (d2 nil)))

    (let [m (d2 "/?foo=bar")]
      (is (and (contains? m :query-params)
               (contains? (:query-params m) :foo))))

    (let [s "abc123 !@#$%^&*"
          [p1 p2] (take 2 (iterate #(apply str (shuffle %)) s))
          r (str "/?"
                 "foo=" (js/encodeURIComponent p1)
                 "&bar=" (js/encodeURIComponent p2))]
      (is (= (:query-params (d2 r))
             {:foo p1 :bar p2})))

    (testing "uri-dispatcher (regex-routes)"
      (is (= (d2 "/ur2/abc")
             ["abc" {}]))

      (is (= (d2 "/ur2/abc?flavor=pineapple&walnuts=true")
             ["abc" {:flavor "pineapple" :walnuts "true"}]))

      (is (= (d2 "/ur3/xyz/123")
             ["xyz" "123"])))

    (testing "uri-dispatcher (vector routes)"
      (is (= (d2 "/ur4/bacon/socks") nil))
      (is (= (d2 "/ur4/123/socks") "123socks"))
      (is (= (d2 "/ur5/123") "123")))))

;; ---------------------------------------------------------------------
;; Render testing

(s/defroute render1 "/render1/:id" [])
(s/defroute render2 ["/render2/:id" :id #"[0-9]+"] [])
(s/defroute render3 ["/render3/:id" :id number?] [])

(deftest render-test
  (testing "calling a route renders it"
    (is (= (render1 {:id 10})
           "/render1/10"))
    (is (= (render2 {:id "10"})
           "/render2/10"))
    (is (thrown? ExceptionInfo (render2 {:id "a"})))
    (is (= (render3 {:id 10})
           "/render3/10"))
    (is (thrown? ExceptionInfo (render3 {:id "dip"})))))
