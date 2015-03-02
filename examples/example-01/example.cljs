(ns example
  (:require [secretary.core :as secretary]
            [goog.events :as events])
  (:import goog.History
           goog.history.EventType))

(def application
  (js/document.getElementById "application"))

(defn set-html! [el content]
  (aset el "innerHTML" content))

(secretary/set-config! :prefix "#")

;; /#/
(secretary/defroute home-path "/" []
  (set-html! application "<h1>OMG! YOU'RE HOME!</h1>"))

;; /#/users
(secretary/defroute user-path "/users" []
  (set-html! application "<h1>USERS!</h1>"))

;; /#/users/:id
(secretary/defroute user-path "/users/:id" [id]
  (let [message (str "<h1>HELLO USER <small>" id "</small>!</h1>")]
    (set-html! application message)))

;; /#/777
(secretary/defroute jackpot-path "/777" []
  (set-html! application "<h1>YOU HIT THE JACKPOT!</h1>"))

;; Catch all
(secretary/defroute "*" []
  (set-html! application "<h1>LOL! YOU LOST!</h1>"))

;; Quick and dirty history configuration.
(let [h (History.)]
  (goog.events/listen h EventType.NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto h
    (.setEnabled true)))

(secretary/dispatch! "/")
