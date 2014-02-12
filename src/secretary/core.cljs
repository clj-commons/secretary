(ns secretary.core
  (:require [clojure.string :as string]))

;; Configuration

(def ^:dynamic *config* (atom {:prefix ""}))

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

;; Parameter encoding/decoding

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

;; Route compilation

(defn re-matches* [re s]
  (let [ms (clojure.core/re-matches re s)]
    (when ms
      (if (sequential? ms) ms [ms ms]))))

(def ^:private re-escape-chars
  (set "\\.*+|?()[]{}$^"))

(defn- re-escape [s]
 (reduce
  (fn [s c]
    (if (re-escape-chars c)
      (str s \\ c)
      (str s c)))
  ""
  s))

(defn- lex* [s clauses]
  (some
   (fn [[re action]]
     (when-let [[m c] (re-find re s)]
       [(subs s (count m)) (action c)]))
   clauses))

(defn- lex-route [s clauses]
  (loop [s s pattern "" params []]
    (if (seq s)
      (let [[s [r p]] (lex* s clauses)]
        (recur s (str pattern r) (conj params p)))
      [(re-pattern (str \^ pattern \$)) (remove nil? params)])))

(defprotocol IRouteMatches
  (route-matches [this route]))

(defn compile-route [route]
  (let [clauses [[#"^\*([^\s.:*/]*)" ;; Splats, named splates
                  (fn [v]
                    (let [r "(.*?)"
                          p (if (seq v)
                              (keyword v)
                              :*)]
                      [r p]))]
                 [#"^\:([^\s.:*/]+)" ;; Params
                  (fn [v]
                    (let [r "([^,;/]+)"
                          p (keyword v)]
                      [r p]))]
                 [#"^([^:*]+)" ;; Literals 
                  (fn [v]
                    (let [r (re-escape v)]
                      [r]))]]
       [re params] (lex-route route clauses)]
   (reify IRouteMatches
     (route-matches [_ route]
       (when-let [[_ & ms] (re-matches* re (js/decodeURIComponent route))]
         (->> (interleave params ms)
              (partition 2)
              (merge-with vector {})))))))

;; Routes adding/removing

(def ^:dynamic *routes*
  (atom []))

(defn add-route! [route action]
  (let [compiled-route (if (satisfies? IRouteMatches route)
                         route
                         (compile-route route))]
    (swap! *routes* conj [compiled-route action route])))

(defn remove-route! [route]
  (swap! *routes*
         (fn [rs]
           (filterv
            (fn [[_ _ route-source]]
              (not= route route-source))
            rs))))

(defn reset-routes! []
  (reset! *routes* []))

;; Route lookup and dispatch

(defn- locate-route [route]
 (some
  (fn [[compiled-route action]]
    (when-let [params (route-matches compiled-route route)]
      [action (route-matches compiled-route route)]))
  @*routes*))

(defn dispatch!
  "Dispatch an action for a given route if it matches the URI path"
  [uri]
  (let [[uri-path query-string] (string/split uri #"\?")
        query-params (when query-string
                       {:query-params (decode-query-params query-string)})
        [action params] (locate-route uri-path)
        action (or action identity)
        params (merge params query-params)]
    (action params)))

;; Route rendering

(defn render-route
  ([route {:keys [query-params] :as m}]
     (let [a (atom m)
           path (.replace route (js/RegExp. ":[^\\s.:*/]+|\\*[^\\s.:*/]*" "g")
                          (fn [$1] (let [lookup (keyword (subs $1 1))
                                         v (@a lookup)]
                                     (if (sequential? v)
                                       (do
                                         (swap! a assoc lookup (next v))
                                         (first v))
                                       (or v $1)))))
           path (str (get-config [:prefix]) path)]
       (if-let [query-string (and query-params
                                  (encode-query-params query-params))]
         (str path "?" query-string)
         path)))
  ([route params opts]
     (render-route route (merge params opts)))
  ([route]
     (render-route route {})))
