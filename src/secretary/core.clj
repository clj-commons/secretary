(ns secretary.core)

(defmacro defroute
  "Add a route to the dispatcher."
  [route destruct & body]
  (let [destruct (if (vector? destruct)
                   {:keys destruct}
                   destruct)]
    `(swap! secretary.core/*routes* assoc ~route
            (fn [params#]
              (let [~destruct params#]
                ~@body)))))
