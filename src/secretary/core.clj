(ns secretary.core)

(defn ^:private route-action-form [destruct body]
  (let [params (gensym)]
    `(fn [~params]
       (cond
        (map? ~params)
        (if (:ring-route? (meta ~params))
          ~(cond
            (map? destruct)
            `(let [~destruct ~params]
               ~@body)

            (vector? destruct)
            `(if (map? (:params ~params))
               (let [{:keys ~destruct} (:params ~params)]
                 ~@body)
               (let [~destruct (:params ~params)]
                 ~@body)))
          (let [~(if (vector? destruct)
                   {:keys destruct}
                   destruct) ~params]
            ~@body))

        (vector? ~params)
        (let [~destruct ~params]
          ~@body)))))

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
