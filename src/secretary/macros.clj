(ns secretary.macros)

(defmacro defroute
  "Add a route to the dispatcher"
  [route destruct & body]
  `(swap! secretary.core/*routes* assoc ~route (fn [params#]
                                                       (let [~destruct params#]
                                                         ~@body))))

