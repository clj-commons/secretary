# secretary

A client-side router for ClojureScript.

## Usage

In your `project.clj`:

```clojure
[secretary "0.7.1"]
```

Using with your app:

```clojure
(ns app
  (:require [secretary.core :as secretary :include-macros true :refer [defroute]]))

(defroute "/users/:id/food/:name" {:as params}
  (js/console.log  (str "User: " (:id params) " Food: " (:name params))))

(defroute "/users/:id" {:keys [id]}
  (js/console.log (str "User: " id)))

(secretary/dispatch! "/users/gf3/food/pizza") ; "User: gf3 Food: pizza"
```

## Contributors

* [@gf3](https://github.com/gf3) (Gianni Chiappetta)
* [@noprompt](https://github.com/noprompt) (Joel Holdbrooks)
* [@joelash](https://github.com/joelash) (Joel Ash)

## License

Distributed under the Eclipse Public License, the same as Clojure.
