(ns secretary.core
  (:require [clojure.string :as string]))

(def ^:dynamic *routes* (atom {}))

(def ^:private slash (new js/RegExp "/"))

(defn- component-matches? [r u]
  (if (= (first r) \:)
    true
    (= r u)))

(defn- extract-component [r u]
  (when (= (first r) \:)
    {(keyword (apply str (rest r))) u}))

(defn route-matches?
  "A predicate to determine if a route matches a URI path"
  [route uri-path]
  (let [r (string/split route slash)
        u (string/split uri-path slash)]
    (when (= (count r) (count u))
      (every? true? (map #(component-matches? %1 %2) r u)))))

(defn any-matches?
  "Determines if there are any routes that match a given URI path"
  [uri-path]
  (some #(route-matches? (first %) uri-path) @*routes*))

(defn extract-components
  "Extract the match data from the URI path into a hash map"
  [route uri-path]
  (when (route-matches? route uri-path)
    (apply merge
           (for [z (zipmap (string/split route slash) (string/split uri-path slash))
                 :let [c (apply extract-component z)]
                 :when (not (nil? c))]
             c))))

(defn dispatch!
  "Dispatch an action for a given route if it matches the URI path"
  [uri-path]
  (when-first [[route action] (filter #(route-matches? (first %) uri-path) @*routes*)]
    (action (extract-components route uri-path))))

