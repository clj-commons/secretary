(ns secretary.core)

(defmacro ^{:arglists '([name? route destruct & body])}
  ROUTE
  [route destruct & body]
  (let [[fn-name route destruct body] (if (symbol? route)
                                        [route destruct (first body) (rest body)]
                                        [nil route destruct body])
        fn-spec `([& args#]
                    (apply secretary.core/render-route* ~route args#))
        fn-def (if fn-name
                 (concat (list 'defn fn-name) fn-spec))]

    (when-not ((some-fn map? vector?) destruct)
      (throw (IllegalArgumentException. (str "route bindings must be a map or vector, given " (pr-str destruct)))))

    `(let [action# (fn [params#]
                     (cond
                      (map? params#)
                      (let [~(if (vector? destruct)
                               {:keys destruct}
                               destruct) params#]
                        ~@body)

                      (vector? params#)
                      (let [~destruct params#]
                        ~@body)))]
       ~fn-def
       [~route action#])))

(defmacro ^{:arglists '([name? route destruct & body])}
  defroute
  "Add a route to the dispatcher."
  [route destruct & body]
  (let [[fn-name route destruct body] (if (symbol? route)
                                        [route destruct (first body) (rest body)]
                                        [nil route destruct body])
        fn-spec `([& args#]
                    (apply secretary.core/render-route* ~route args#))
        fn-body (if fn-name
                  (concat (list 'defn fn-name) fn-spec)
                  (cons 'fn fn-spec))]
    `(let [[route# action#] (ROUTE ~route ~destruct ~@body)]
       (secretary.core/add-route! ~route action#)
       ~fn-body)))

(defmacro defroutes [sym & body]
  "Define an instance of secretary.core/Routes named sym."
  `(def ~sym
     (let [rfn# (fn [routes# x#]
                  (if (instance? secretary.core/Routes x#)
                    (into routes# x#)
                    (conj routes# x#)))]
       (reduce rfn# (secretary.core/Routes. []) ~(vec body)))))


