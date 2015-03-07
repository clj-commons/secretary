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

(defmacro ^{:arglists '([name route destruct & body])}
  defroute
  "Define an instance of secretary.core/Route."
  [name pattern destruct & body]
  (when-not (or (map? destruct) (vector? destruct))
    (throw (IllegalArgumentException.
            (str "defroute bindings must be a map or vector, given " (pr-str destruct)))))
  `(def ~name
     (secretary.core/make-route ~pattern ~(route-action-form destruct body))))
