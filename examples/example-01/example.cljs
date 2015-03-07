(ns example-01.example
  (:require [secretary.core :as secretary]
            [goog.events :as events])
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.History
           goog.history.EventType))

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
(defroute wildcard "*" []
  (set-html! application "<h1>LOL! YOU LOST!</h1>"))

(def dispatch!
  (secretary/uri-dispatcher
   [home-path
    users-path
    user-path
    jackpot-path
    wildcard]))

;; Quick and dirty history configuration.
(let [h (History.)]
  (goog.events/listen h EventType.NAVIGATE #(dispatch! (.-token %)))
  (doto h
    (.setEnabled true)))

(dispatch! "/")
