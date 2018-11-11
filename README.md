# secretary

A client-side router for ClojureScript.

[![CircleCI](https://circleci.com/gh/clj-commons/secretary.svg?style=svg)](https://circleci.com/gh/clj-commons/secretary)


## Contents

- [Installation](#installation)
- [Guide](#guide)
  * [Basic routing and dispatch](#basic-routing-and-dispatch)
  * [Route matchers](#route-matchers)
  * [Parameter destructuring](#parameter-destructuring)
  * [Query parameters](#query-parameters)
  * [Named routes](#named-routes)
- [Example with history](#example-with-googhistory)
- [Available protocols](#available-protocols)
- [Contributors](#contributors)
- [Committers](#committers)


## Installation

Add secretary to your `project.clj` `:dependencies` vector:

```clojure
[clj-commons/secretary "1.2.4"]
```

For the current `SNAPSHOT` version use:

```clojure
[clj-commons/secretary "1.2.5-SNAPSHOT"]
```

## Guide

To get started `:require` secretary somewhere in your project.

```clojure
(ns app.routes
  (:require [secretary.core :as secretary :refer-macros [defroute]]))
```
**Note**: starting ClojureScript v0.0-2371, `:refer` cannot be used to import macros into your project anymore. The proper way to do it is by using `:refer-macros` as above. When using ClojureScript v0.0-2755 or above, if `(:require [secretary.core :as secretary])` is used, macros will be automatically aliased to `secretary`, e.g. `secretary/defroute`.

### Basic routing and dispatch

Secretary is built around two main goals: creating route matchers
and dispatching actions. Route matchers match and extract
parameters from URI fragments and actions are functions which
accept those parameters.

`defroute` is Secretary's primary macro for defining a link between a
route matcher and an action. The signature of this macro is
`[name? route destruct & body]`. We will skip the `name?` part
of the signature for now and return to it when we discuss
[named routes](#named-routes). To get clearer picture of this
let's define a route for users with an id.

```clojure
(defroute "/users/:id" {:as params}
  (js/console.log (str "User: " (:id params))))
```

In this example `"/users/:id"` is `route`, the route matcher,
`{:as params}` is `destruct`, the destructured parameters extracted
from the route matcher result, and the remaining
`(js/console.log ...)` portion is `body`, the route action.

Before going in to more detail let's try to dispatch our route.

```clojure
(secretary/dispatch! "/users/gf3")
```

With any luck, when we refresh the page and view the console we should
see that `User: gf3` has been logged somewhere.


#### Route matchers

By default the route matcher may either be a string or regular
expression. String route matchers have special syntax which may be
familiar to you if you've worked with [Sinatra][sinatra] or
[Ruby on Rails][rails]. When `secretary/dispatch!` is called with a
URI it attempts to find a route match and it's corresponding
action. If the match is successful, parameters will be extracted from
the URI. For string route matchers these will be contained in a map;
for regular expressions a vector.

In the example above, the route matcher
`"/users/:id"` successfully matched against `"/users/gf3"` and
extracted `{:id "gf3}` as parameters. You can refer to the table below
for more examples of route matchers and the parameters they return
when matched successfully.

Route matcher        | URI              | Parameters
---------------------|------------------|--------------------------
`"/:x/:y"`           | `"/foo/bar"`     | `{:x "foo" :y "bar"}`
`"/:x/:x"`           | `"/foo/bar"`     | `{:x ["foo" "bar"]}`
`"/files/*.:format"`  | `"/files/x.zip"` | `{:* "x" :format "zip"}`
`"*"`                | `"/any/thing"`   | `{:* "/any/thing"}`
`"/*/*"`             | `"/n/e/thing"`   | `{:* ["n" "e/thing"]}`
`"/*x/*y"`           | `"/n/e/thing"`   | `{:x "n" :y "e/thing"}`
`#"/[a-z]+/\d+"`     | `"/foo/123"`     | `["/foo/123"]`
`#"/([a-z]+)/(\d+)"` | `"/foo/123"`     | `["foo" "123"]`


#### Parameter destructuring

Now that we understand what happens during dispatch we can look at the
`destruct` argument of `defroute`. This part is literally sugar
around `let`. Basically whenever one of our route matches is
successful and extracts parameters this is where we destructure
them. Under the hood, for example with our users route, this looks
something like the following.

```clojure
(let [{:as params} {:id "gf3"}]
  ...)
```

Given this, it should be fairly easy to see that we could have have
written

```clojure
(defroute "/users/:id" {id :id}
  (js/console.log (str "User: " id)))
```

and seen the same result. With string route matchers we can go even
further and write

```clojure
(defroute "/users/:id" [id]
  (js/console.log (str "User: " id)))
```

which is essentially the same as saying `{:keys [id]}`.

For regular expression route matchers we can only use vectors for
destructuring since they only ever return vectors.

```clojure
(defroute #"/users/(\d+)" [id]
  (js/console.log (str "User: " id)))
```


#### Query parameters

If a URI contains a query string it will automatically be extracted to
`:query-params` for string route matchers and to the last element for
regular expression matchers.

```clojure
(defroute "/users/:id" [id query-params]
  (js/console.log (str "User: " id))
  (js/console.log (pr-str query-params)))

(defroute #"/users/(\d+)" [id {:keys [query-params]}]
  (js/console.log (str "User: " id))
  (js/console.log (pr-str query-params)))

;; In both instances...
(secretary/dispatch! "/users/10?action=delete")
;; ... will log
;; User: 10
;; "{:action \"delete\"}"
```


#### Named routes

While route matching and dispatch is by itself useful, it is often
necessary to have functions which take a map of parameters and return
a URI. By passing an optional name to `defroute` Secretary will
define this function for you.

```clojure
(defroute users-path "/users" []
  (js/console.log "Users path"))

(defroute user-path "/users/:id" [id]
  (js/console.log (str "User " id "'s path"))

(users-path) ;; => "/users"
(user-path {:id 1}) ;; => "/users/1"
```

This also works with `:query-params`.

```clojure
(user-path {:id 1 :query-params {:action "delete"}})
;; => "/users/1?action=delete"
```

If the browser you're targeting does not support HTML5 history you can
call

```clojure
(secretary/set-config! :prefix "#")
```

to prefix generated URIs with a "#".

```clojure
(user-path {:id 1})
;; => "#/users/1"
```


### Available protocols

You can extend Secretary's protocols to your own data types and
records if you need special functionality.

- [`IRenderRoute`](#irenderroute)
- [`IRouteMatches`](#iroutematches)


#### `IRenderRoute`

Most of the time the defaults will be good enough but on occasion you
may need custom route rendering. To do this implement `IRenderRoute`
for your type or record.

```clojure
(defrecord User [id]
  secretary/IRenderRoute
  (render-route [_]
    (str "/users/" id))

  (render-route [this params]
    (str (secretary/render-route this) "?"
         (secretary/encode-query-params params))))

(secretary/render-route (User. 1))
;; => "/users/1"
(secretary/render-route (User. 1) {:action :delete})
;; => "/users/1?action=delete"
```


#### `IRouteMatches`

It is seldom you will ever need to create your own route matching
implementation as the built in `String` and `RegExp` routes matchers
should be fine for most applications. Still, if you have a suitable
use case then this protocol is available. If your intention is to is
to use it with `defroute` your implementation must return a map or
vector.


### Example with `goog.History`

```clojure
(ns example
  (:require [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(def application
  (js/document.getElementById "application"))

(defn set-html! [el content]
  (aset el "innerHTML" content))

(secretary/set-config! :prefix "#")

;; /#/
(defroute home-path "/" []
  (set-html! application "<h1>OMG! YOU'RE HOME!</h1>"))

;; /#/users
(defroute users-path "/users" []
  (set-html! application "<h1>USERS!</h1>"))

;; /#/users/:id
(defroute user-path "/users/:id" [id]
  (let [message (str "<h1>HELLO USER <small>" id "</small>!</h1>")]
    (set-html! application message)))

;; /#/777
(defroute jackpot-path "/777" []
  (set-html! application "<h1>YOU HIT THE JACKPOT!</h1>"))

;; Catch all
(defroute "*" []
  (set-html! application "<h1>LOL! YOU LOST!</h1>"))

;; Quick and dirty history configuration.
(let [h (History.)]
  (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto h (.setEnabled true)))
```


## Contributors

* [@gf3](https://github.com/gf3) (Gianni Chiappetta)
* [@noprompt](https://github.com/noprompt) (Joel Holdbrooks)
* [@joelash](https://github.com/joelash) (Joel Friedman)
* [@james-henderson](https://github.com/james-henderson) (James Henderson)
* [@the-kenny](https://github.com/the-kenny) (Moritz Ulrich)
* [@timgilbert](https://github.com/timgilbert) (Tim Gilbert)
* [@bbbates](https://github.com/bbbates) (Brendan)
* [@travis](https://github.com/travis) (Travis Vachon)

## Committers

* [@gf3](https://github.com/gf3) (Gianni Chiappetta)
* [@noprompt](https://github.com/noprompt) (Joel Holdbrooks)
* [@joelash](https://github.com/joelash) (Joel Friedman)

## License

Distributed under the Eclipse Public License, the same as Clojure.

[sinatra]: http://www.sinatrarb.com/intro.html#Routes
[rails]: http://guides.rubyonrails.org/routing.html
