(ns secretary.core
  (:require [clojure.string :as string]))

(def ^:dynamic *config* (atom {:prefix ""}))

(def ^:dynamic *routes* (atom {}))

(def ^:private slash #"/")

(defn get-config
  "Gets a value for *config* at path."
  [path]
  (let [path (if (sequential? path) path [path])]
    (get-in @*config* path)))

(defn set-config!
  "Associates a value val for *config* at path."
  [path val]
  (let [path (if (sequential? path) path [path])]
    (swap! *config* assoc-in path val)))

(defn- param? [r]
  (= (first r) \:))

(defn- component-matches? [r u]
  (or (param? r) (= r u)))

(defn- extract-component [r u]
  (when (param? r)
    {(keyword (subs r 1)) u}))

(defn- exact-match? [r u]
  (= r u))

(defn route-matches?
  "A predicate to determine if a route matches a URI path."
  [route uri-path]
  (let [r (string/split route slash)
        u (string/split uri-path slash)]
    (when (= (count r) (count u))
      (every? true? (map #(component-matches? %1 %2) r u)))))

(defn filter-routes [pred uri-path]
  (filter #(pred (first %) uri-path) @*routes*))

(defn encode-query-params
  "Turns a map of query parameters into url encoded string."
  [query-params]
  (->> (map
        (fn [[k v]] (str (name k) "=" (js/encodeURIComponent (str v))))
        query-params)
       (string/join "&")))

(defn decode-query-params
  "Extract a map of query parameters from a query string."
  [query-string]
  (reduce
   (fn [m param]
     (let [[k v] (string/split param #"=" 2)
           v (js/decodeURIComponent v)]
       (assoc m k v)))
   {}
   (string/split query-string #"&")))

;; Temporary alias.
(def parse-query-params decode-query-params)

(defn extract-components
  "Extract the match data from the URI path into a hash map"
  [route uri-path]
  (when (route-matches? route uri-path)
    (apply merge
      (for [z (zipmap (string/split route slash) (string/split uri-path slash))
            :let [c (apply extract-component z)]
            :when (not (nil? c))]
        c))))

(defn parse-action [uri-path]
 (if-let [[route action] (first (filter-routes exact-match? uri-path))]
   [action {}]
   (when-first [[route action] (filter-routes route-matches? uri-path)]
     [action (extract-components route uri-path)])))

(defn dispatch!
  "Dispatch an action for a given route if it matches the URI path"
  [uri]
  (let [[uri-path query-string] (string/split uri #"\?")
        query-params (when query-string
                       {:query-params (decode-query-params query-string)})
        [action params] (parse-action uri-path)
        action (or action identity)
        params (merge params query-params)]
    (action params)))

(defn render-route
  ([route {:keys [query-params] :as m}]
     (let [path (.replace route (js/RegExp. ":[^/]+" "g")
                          (fn [$1] (let [lookup (keyword (subs $1 1))]
                                     (m lookup $1))))
           path (str (get-config [:prefix]) path)]
       (if-let [query-string (and query-params
                                  (encode-query-params query-params))]
         (str path "?" query-string)
         path)))
  ([route params opts]
     (render-route route (merge params opts)))
  ([route]
     (render-route route {})))
