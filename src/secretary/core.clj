(ns secretary.core)

(defmacro ^{:arglists '([name? route destruct & body])}
  defroute
  "Add a route to the dispatcher."
  [route destruct & body]
  (let [[fn-name route destruct body] (if (symbol? route)
                                        [route destruct (first body) (rest body)]
                                        [nil route destruct body])
        destruct (if (vector? destruct)
                   {:keys destruct}
                   destruct)
        fn-spec `([& args#]
                    (apply secretary.core/render-route* ~route args#))
        fn-body (if fn-name
                  (concat (list 'defn fn-name) fn-spec)
                  (cons 'fn fn-spec))]
    `(do
       (let [action# (fn [params#]
                       (let [~destruct params#]
                         ~@body))]
         (secretary.core/add-route! ~route action#))
       ~fn-body)))
