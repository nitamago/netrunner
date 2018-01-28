(ns test.core
  (:require [game.utils :refer [remove-once has? merge-costs zone make-cid to-keyword capitalize
                                costs-to-symbol vdissoc distinct-by]]
            [game.macros :refer [effect req msg]]
            [clojure.string :refer [split-lines split join]]
            [game.core :as core :refer [all-cards]]
            [test.utils :refer [load-card load-cards qty default-corp default-runner
                                make-deck]]
            [test.macros :refer [do-game]]
            [clojure.test :refer :all]))

;;; Click action functions
(defn take-credits
  "Take credits for n clicks, or if no n given, for all remaining clicks of a side.
  If all clicks are used up, end turn and start the opponent's turn."
  ([state side] (take-credits state side nil))
  ([state side n]
    (let  [remaining-clicks (get-in @state [side :click])
           n (or n remaining-clicks)
           other (if (= side :corp) :runner :corp)]
      (dotimes [i n] (core/click-credit state side nil))
      (if (= (get-in @state [side :click]) 0)
        (do (core/end-turn state side nil)
            (core/start-turn state other nil))))))

(defn new-game
  "Init a new game using given corp and runner. Keep starting hands (no mulligan) and start Corp's turn."
  ([corp runner] (new-game corp runner nil))
  ([corp runner {:keys [mulligan start-as dont-start-turn dont-start-game] :as args}]
    (let [states (core/init-game
                   {:gameid 1
                    :players [{:side "Corp"
                               :deck {:identity (@all-cards (:identity corp))
                                      :cards (:deck corp)}}
                              {:side "Runner"
                               :deck {:identity (@all-cards (:identity runner))
                                      :cards (:deck runner)}}]})
          state (second (last states))]
      (when-not dont-start-game
        (if (#{:both :corp} mulligan)
          (core/resolve-prompt state :corp {:choice "Mulligan"})
          (core/resolve-prompt state :corp {:choice "Keep"}))
        (if (#{:both :runner} mulligan)
          (core/resolve-prompt state :runner {:choice "Mulligan"})
          (core/resolve-prompt state :runner {:choice "Keep"}))
        (when-not dont-start-turn (core/start-turn state :corp nil))
        (when (= start-as :runner) (take-credits state :corp)))
      state)))

(defn load-all-cards []
  (reset! game.core/all-cards (into {} (map (juxt :title identity) (map #(assoc % :cid (make-cid)) (load-cards))))))
(load-all-cards)

;;; Card related functions
(defn find-card
  "Return a card with given title from given sequence"
  [title from]
  (some #(when (= (:title %) title) %) from))

(defn card-ability
  "Trigger a card's ability with its 0-based index. Refreshes the card argument before
  triggering the ability."
  ([state side card ability] (card-ability state side card ability nil))
  ([state side card ability targets]
   (core/play-ability state side {:card (core/get-card state card)
                                  :ability ability :targets targets})))

(defn card-subroutine
  "Trigger a piece of ice's subroutine with the 0-based index."
  ([state side card ability] (card-subroutine state side card ability nil))
  ([state side card ability targets]
   (core/play-subroutine state side {:card (core/get-card state card)
                                     :subroutine ability :targets targets})))

(defn get-ice
  "Get installed ice protecting server by position."
  [state server pos]
  (get-in @state [:corp :servers server :ices pos]))

(defn get-content
  "Get card in a server by position. If no pos, get all cards in the server."
  ([state server]
   (get-in @state [:corp :servers server :content]))
  ([state server pos]
   (get-in @state [:corp :servers server :content pos])))

(defn get-program
  "Get non-hosted program by position."
  ([state] (get-in @state [:runner :rig :program]))
  ([state pos]
   (get-in @state [:runner :rig :program pos])))

(defn get-hardware
  "Get hardware by position."
  ([state] (get-in @state [:runner :rig :hardware]))
  ([state pos]
   (get-in @state [:runner :rig :hardware pos])))

(defn get-resource
  "Get non-hosted resource by position."
  [state pos]
  (get-in @state [:runner :rig :resource pos]))

(defn get-scored
  "Get a card from the score area. Can find by name or index.
  If no index or name provided, get the first scored agenda."
  ([state side] (get-scored state side 0))
  ([state side x]
   (if (number? x)
     ;; Find by index
     (get-in @state [side :scored x])
     ;; Find by name
     (when (string? x)
       (find-card x (get-in @state [side :scored]))))))

(defn get-counters
  "Get number of counters of specified type."
  [card type]
  (get-in card [:counter type] 0))

(defn play-from-hand
  "Play a card from hand based on its title. If installing a Corp card, also indicate
  the server to install into with a string."
  ([state side title] (play-from-hand state side title nil))
  ([state side title server]
    (core/play state side {:card (find-card title (get-in @state [side :hand]))
                           :server server})))


;;; Run functions
(defn play-run-event
  "Play a run event with a replace-access effect on an unprotected server.
  Advances the run timings to the point where replace-access occurs."
  [state card server]
  (core/play state :runner {:card card})
  (is (= [server] (get-in @state [:run :server])) "Correct server is run")
  (is (get-in @state [:run :run-effect]) "There is a run-effect")
  (core/no-action state :corp nil)
  (core/successful-run state :runner nil)
  (is (get-in @state [:runner :prompt]) "A prompt is shown")
  (is (get-in @state [:run :successful]) "Run is marked successful"))

(defn run-on
  "Start run on specified server."
  [state server]
  (core/click-run state :runner {:server server}))

(defn run-continue
  "No action from corp and continue for runner to proceed in current run."
  [state]
  (core/no-action state :corp nil)
  (core/continue state :runner nil))

(defn run-phase-43
  "Ask for triggered abilities phase 4.3"
  [state]
  (core/corp-phase-43 state :corp nil)
  (core/successful-run state :runner nil))

(defn run-successful
  "No action from corp and successful run for runner."
  [state]
  (core/no-action state :corp nil)
  (core/successful-run state :runner nil))

(defn run-jack-out
  "Jacks out in run."
  [state]
  (core/jack-out state :runner nil))

(defn run-empty-server
  "Make a successful run on specified server, assumes no ice in place."
  [state server]
  (run-on state server)
  (run-successful state))


;;; Misc functions
(defn score-agenda
  "Take clicks and credits needed to advance and score the given agenda."
  ([state _ card]
   (let [title (:title card)
         advancementcost (:advancementcost card)]
    (core/gain state :corp :click advancementcost :credit advancementcost)
    (dotimes [n advancementcost]
      (core/advance state :corp {:card (core/get-card state card)}))
    (is (= advancementcost (get-in (core/get-card state card) [:advance-counter])))
    (core/score state :corp {:card (core/get-card state card)})
    (is (find-card title (get-in @state [:corp :scored]))))))

(defn advance
  "Advance the given card."
  ([state card] (advance state card 1))
  ([state card n]
   (dotimes [_ n]
     (core/advance state :corp {:card (core/get-card state card)}))))

(defn last-log-contains?
  [state content]
  (not (nil?
         (re-find (re-pattern content)
                  (get (last (get @state :log)) :text)))))

(defn second-last-log-contains?
  [state content]
  (not (nil?
         (re-find (re-pattern content)
                  (get (last (butlast (get @state :log))) :text)))))

(defn trash-from-hand
  "Trash specified card from hand of specified side"
  [state side title]
  (core/trash state side (find-card title (get-in @state [side :hand]))))

(defn trash-resource
  "Trash specified card from rig of the runner"
  [state title]
  (core/trash state :runner (find-card title (get-in @state [:runner :rig :resource]))))

(defn starting-hand
  "Moves all cards in the player's hand to their draw pile, then moves the specified card names
  back into the player's hand."
  [state side cards]
  (doseq [c (get-in @state [side :hand])]
    (core/move state side c :deck))
  (doseq [ctitle cards]
    (core/move state side (find-card ctitle (get-in @state [side :deck])) :hand)))

(defn accessing
  "Checks to see if the runner has a prompt accessing the given card title"
  [state title]
  (= title (-> @state :runner :prompt first :card :title)))

(load "core-game")
