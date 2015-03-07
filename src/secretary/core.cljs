(ns secretary.core
  (:require
   [clojure.string :as string]
   [secretary.codec :as codec])
  (:require-macros
   [secretary.core :refer [defroute]]))

;; ---------------------------------------------------------------------
;; Protocols

(defprotocol IRouteMatches
  (-route-matches [this x]))

(defprotocol IRouteValue
  (-route-value [this]))

(defprotocol IRenderRoute
  (-render-route [this] [this params]))

(defn route-matches
  "Extract matches from x with route. route must satisfy IRouteMatches."
  [route x]
  (-route-matches route x))

(defn route-value
  "Return the value for a route. route must satisfy IRouteValue."
  [route]
  (-route-value route))

(defn render-route
  "Return a representation of route optionally with params. route must 
  satisfy IRenderRoute."
  ([route] (-render-route route))
  ([route params] (-render-route route params)))


;; ---------------------------------------------------------------------
;; Route compilation

;; The implementation for route compilation was inspired by Clout and
;; modified to suit JavaScript and Secretary.
;; SEE: https://github.com/weavejester/clout

(def ^:private re-escape-chars
  (set "\\.*+|?()[]{}$^"))

(defn ^:private re-matches*
  "Like re-matches but result is a always vector. If re does not
  capture matches then it will return a vector of [m m] as if it had a
  single capture. Other wise it maintains consistent behavior with
  re-matches. "
  [re s]
  (let [ms (clojure.core/re-matches re s)]
    (when ms
      (if (sequential? ms) ms [ms ms]))))

(defn ^:private re-escape [s]
  (reduce
   (fn [s c]
     (if (re-escape-chars c)
       (str s \\ c)
       (str s c)))
   ""
   s))

(defn ^:private lex*
  "Attempt to lex a single token from s with clauses. Each clause is a
  pair of [regexp action] where action is a function. regexp is
  expected to begin with ^ and contain a single capture. If the
  attempt is successful a vector of [s-without-token (action capture)]
  is returned. Otherwise the result is nil."
  [s clauses]
  (some
   (fn [[re action]]
     (when-let [[m c] (re-find re s)]
       [(subs s (count m)) (action c)]))
   clauses))

(defn ^:private lex-route
  "Return a pair of [regex params]. regex is a compiled regular
  expression for matching routes. params is a list of route param
  names (:*, :id, etc.). "
  [s clauses]
  (loop [s s pattern "" params []]
    (if (seq s)
      (let [[s [r p]] (lex* s clauses)]
        (recur s (str pattern r) (conj params p)))
      [(re-pattern (str \^ pattern \$)) (remove nil? params)])))

(defn ^:private compile-route
  "Given a string route return an instance of IRouteMatches."
  [^string s]
  (let [clauses [[#"^\*([^\s.:*/]*)" ;; Splats, named splates
                  (fn [v]
                    (let [r "(.*?)"
                          p (if (seq v)
                              (keyword v)
                              :*)]
                      [r p]))]
                 [#"^\:([^\s.:*/]+)" ;; Params
                  (fn [v]
                    (let [r "([^,;?/]+)"
                          p (keyword v)]
                      [r p]))]
                 [#"^([^:*]+)" ;; Literals
                  (fn [v]
                    (let [r (re-escape v)]
                      [r]))]]
        [re params] (lex-route s clauses)]
    (reify
      IRouteValue
      (-route-value [this] s)

      IRouteMatches
      (-route-matches [_ route]
        (when-let [[_ & ms] (re-matches* re route)]
          (->> (interleave params (map js/decodeURIComponent ms))
               (partition 2)
               (merge-with vector {}))))

      IRenderRoute
      (-render-route [_]
        (-render-route s))

      (-render-route [_ params]
        (-render-route s params)))))


;; ---------------------------------------------------------------------
;; Route

(defrecord Route [pattern action]
  IRouteValue
  (-route-value [_]
    (if (satisfies? IRouteValue pattern)
      (-route-value pattern)
      pattern))

  IRouteMatches
  (-route-matches [_ x]
    (when (satisfies? IRouteMatches pattern)
      (-route-matches pattern x)))

  IRenderRoute
  (-render-route [this]
    (when (satisfies? IRenderRoute pattern)
      (-render-route pattern)))

  (-render-route [this params]
    (when (satisfies? IRenderRoute pattern)
      (-render-route pattern params)))

  IFn
  (-invoke [this]
    (-render-route this))

  (-invoke [this params]
    (-render-route this params)))

(defn route?
  "Returns true if x is an instance of Route."
  [x]
  (instance? Route x))

(defn make-route
  "Returns an instance of Route given a pattern and action."
  [pattern action]
  {:pre [(ifn? action)]}
  (if (string? pattern)
    (Route. (compile-route pattern) action)
    (Route. pattern action)))


;; ---------------------------------------------------------------------
;; URI dispatcher

(defn request-map [s]
  (let [[uri query-string] (string/split s #"\?")]
    {:uri uri
     :query-string query-string}))

(defn wrap-query-params [handler]
  (fn [req]
    (->> (str (:query-string req))
         (codec/decode-query-params)
         (assoc req :query-params)
         (handler))))

(defn wrap-route [handler routes]
  (fn [req]
    (if-let [[route params] (some (fn [r]
                                    (when-let [ms (route-matches r (:uri req))]
                                      [r ms]))
                                  routes)]
      (handler (assoc req :route route :params params))
      (handler req))))

(defn uri-dispatcher
  "Return a dispatcher which when invoked with a uri attempts 
  to locate, match, and apply a routing action. Optionally a ring-style 
  handler may be passed."
  ([routes]
     (uri-dispatcher routes identity))
  ([routes handler]
     (fn [uri]
       (let [h (-> (handler identity)
                   (wrap-route routes)
                   (wrap-query-params))
             {:keys [route] :as req} (h (request-map uri))]
         (when route
           (let [req-map (-> (dissoc req :route)
                             (with-meta {:ring-route? true}))]
             (.action route req-map)))))))


;; ---------------------------------------------------------------------
;; Protocol implementations

;;; string

(extend-type string
  IRouteValue
  (-route-value [this] this)

  IRouteMatches
  (-route-matches [this route]
    (-route-matches (compile-route this) route))

  IRenderRoute
  (-render-route [this]
    (-render-route this {}))

  (-render-route [this params]
    (let [{:keys [query-params] :as m} params
          a (atom m)
          path (.replace this (js/RegExp. ":[^\\s.:*/]+|\\*[^\\s.:*/]*" "g")
                         (fn [$1]
                           (let [lookup (keyword (if (= $1 "*")
                                                   $1
                                                   (subs $1 1)))
                                 v (get @a lookup)
                                 replacement (if (sequential? v)
                                               (do
                                                 (swap! a assoc lookup (next v))
                                                 (codec/encode-uri (first v)))
                                               (if v (codec/encode-uri v) $1))]
                             replacement)))]
      (if-let [query-string (and query-params
                                 (codec/encode-query-params query-params))]
        (str path "?" query-string)
        path))))


;;; RegExp

(extend-type js/RegExp
  IRouteValue
  (-route-value [this] this)

  IRouteMatches
  (-route-matches [this route]
    (when-let [[_ & ms] (re-matches* this route)]
      (vec ms))))


;;; PersistentVector

(defn ^:private invalid-params [params validations]
  (reduce (fn [m [key validation]]
            (if-let [value (get params key)]
              (cond
               (regexp? validation)
               (if (re-matches validation value)
                 m
                 (assoc m key [value validation]))
               (ifn? validation)
               (if (validation value)
                 m
                 (assoc m key [value validation]))
               :else m)))
          {}
          (partition 2 validations)))

(defn ^:private params-valid? [params validations]
  (empty? (invalid-params params validations)))


(extend-type PersistentVector
  IRouteValue
  (-route-value [[route-string & validations]]
    (vec (cons (route-value route-string) validations)))

  IRouteMatches
  (-route-matches [[route-string & validations] route]
    (let [params (route-matches (compile-route route-string) route)]
      (when (params-valid? params validations)
        params)))

  IRenderRoute
  (-render-route [this]
    (-render-route this {}))

  (-render-route [[route-string & validations] params]
    (let [invalid (invalid-params params validations)]
      (if (empty? invalid)
        (render-route route-string params)
        (throw (ex-info "Could not build route: invalid params" invalid))))))
