(ns secretary.core)

(defn ^:private route-action-form [destruct body]
  (let [params (gensym)]
    `(fn [~params]
       (let [~@(cond
                 (or (map? destruct)
                     (vector? params))
                 [destruct params]

                 (:ring-route? (meta params))
                 [{:keys destruct} params]

                 (map? (:params params))
                 [{:keys destruct} (:params params)]

                 (vector? (:params params))
                 [destruct (:params params)])]
         ~@body))))

(defn- binding-exception [type destruct]
  (IllegalArgumentException.
   (str type " bindings must be a map or vector, given " (pr-str destruct))))

(defmacro ^{:arglists '([pattern destruct & body])}
  route
  "Define an anonymous instance of secretary.core/Route."
  [pattern destruct & body]
  (when-not (or (map? destruct) (vector? destruct))
    (throw (binding-exception "route" destruct)))
  `(secretary.core/make-route ~pattern ~(route-action-form destruct body)))

(defmacro ^{:arglists '([name pattern destruct & body])}
  defroute
  "Define a named instance of secretary.core/Route."
  [name pattern destruct & body]
  (when-not (or (map? destruct) (vector? destruct))
    (throw (binding-exception "defroute" destruct)))
  `(def ~name (route ~pattern ~destruct ~body)))
