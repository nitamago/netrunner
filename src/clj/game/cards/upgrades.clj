(in-ns 'game.core)
(declare expose-prevent)

(def cards-upgrades
  {"Akitaro Watanabe"
   {:events {:pre-rez-cost {:req (req (and (ice? target)
                                           (= (card->server state card) (card->server state target))))
                            :effect (effect (rez-cost-bonus -2))}}}

   "Amazon Industrial Zone"
   {:events
     {:corp-install  {:optional {:req (req (and (ice? target)
                                                (protecting-same-server? card target)))
                                 :prompt "Rez ICE with rez cost lowered by 3?" :priority 2
                                 :yes-ability {:effect (effect (rez-cost-bonus -3) (rez target))}}}}}

   "Ash 2X3ZB9CY"
   {:events {:successful-run {:interactive (req true)
                              :req (req this-server)
                              :trace {:base 4
                                      :effect (req (max-access state side 0)
                                                   (when-not (:replace-access (get-in @state [:run :run-effect]))
                                                     (let [ash card]
                                                       (swap! state update-in [:run :run-effect]
                                                              #(assoc % :replace-access
                                                                        {:mandatory true
                                                                         :effect (effect (handle-access [ash])) :card ash})))))
                                      :msg "prevent the Runner from accessing cards other than Ash 2X3ZB9CY"}}}}

   "Awakening Center"
   {:can-host (req (is-type? target "ICE"))
    :abilities [{:label "Host a piece of Bioroid ICE"
                 :cost [:click 1]
                 :prompt "Select a piece of Bioroid ICE to host on Awakening Center"
                 :choices {:req #(and (ice? %)
                                      (has-subtype? % "Bioroid")
                                      (in-hand? %))}
                 :msg "host a piece of Bioroid ICE"
                 :effect (req (corp-install state side target card {:no-install-cost true}))}
                {:req (req (and this-server (= (get-in @state [:run :position]) 0)))
                 :label "Rez a hosted piece of Bioroid ICE"
                 :prompt "Choose a piece of Bioroid ICE to rez" :choices (req (:hosted card))
                 :msg (msg "lower the rez cost of " (:title target) " by 7 [Credits] and force the Runner to encounter it")
                 :effect (effect (rez-cost-bonus -7) (rez target)
                                 (update! (dissoc (get-card state target) :facedown))
                                 (register-events {:run-ends
                                                    {:effect (req (doseq [c (:hosted card)]
                                                                    (when (:rezzed c)
                                                                      (trash state side c)))
                                                                  (unregister-events state side card))}} card))}]
    :events {:run-ends nil}}

   "Bamboo Dome"
   (letfn [(dome [dcard]
             {:prompt "Select a card to add to HQ"
              :delayed-completion true
              :choices {:req #(and (= (:side %) "Corp")
                                   (= (:zone %) [:play-area]))}
              :msg "move a card to HQ"
              :effect (effect (move target :hand)
                              (continue-ability (put dcard) dcard nil))})
           (put [dcard]
             {:prompt "Select first card to put back onto R&D"
              :delayed-completion true
              :choices {:req #(and (= (:side %) "Corp")
                                   (= (:zone %) [:play-area]))}
              :msg "move remaining cards back to R&D"
              :effect (effect (move target :deck {:front true})
                              (move (first (get-in @state [:corp :play-area])) :deck {:front true})
                              (clear-wait-prompt :runner)
                              (effect-completed eid dcard))})]

   {:init {:root "R&D"}
    :abilities [{:cost [:click 1]
                 :req (req (>= (count (:deck corp)) 3))
                 :delayed-completion true
                 :msg (msg (str "reveal " (join ", " (map :title (take 3 (:deck corp)))) " from R&D"))
                 :label "Reveal the top 3 cards of R&D. Secretly choose 1 to add to HQ. Return the others to the top of R&D, in any order."
                 :effect (req (doseq [c (take 3 (:deck corp))]
                                (move state side c :play-area))
                              (show-wait-prompt state :runner "Corp to use Bamboo Dome")
                              (continue-ability state side (dome card) card nil))}]})

   "Ben Musashi"
   (let [bm {:req (req (or (in-same-server? card target)
                           (from-same-server? card target)))
             :effect (effect (steal-cost-bonus [:net-damage 2]))}]
     {:trash-effect
              {:req (req (and (= :servers (first (:previous-zone card))) (:run @state)))
               :effect (effect (register-events {:pre-steal-cost (assoc bm :req (req (or (= (:zone target) (:previous-zone card))
                                                                                         (= (central->zone (:zone target))
                                                                                            (butlast (:previous-zone card))))))
                                                 :run-ends {:effect (effect (unregister-events card))}}
                                                (assoc card :zone '(:discard))))}
      :events {:pre-steal-cost bm :run-ends nil}})

   "Bernice Mai"
   {:events {:successful-run {:interactive (req true)
                              :req (req this-server)
                              :trace {:base 5 :msg "give the Runner 1 tag"
                                      :delayed-completion true
                                      :effect (effect (tag-runner :runner eid 1))
                                      :unsuccessful {:effect (effect (system-msg "trashes Bernice Mai from the unsuccessful trace")
                                                                     (trash card))}}}}}

   "Black Level Clearance"
   {:events {:successful-run
             {:interactive (req true)
              :req (req this-server)
              :delayed-completion true
              :effect (effect (continue-ability
                                {:prompt "Take 1 brain damage or jack out?"
                                 :player :runner
                                 :choices ["Take 1 brain damage" "Jack out"]
                                 :effect (req (if (= target "Take 1 brain damage")
                                                (damage state side eid :brain 1 {:card card})
                                                (do (jack-out state side nil)
                                                    (swap! state update-in [:runner :prompt] rest)
                                                    (close-access-prompt state side)
                                                    (handle-end-run state side)
                                                    (gain state :corp :credit 5)
                                                    (draw state :corp)
                                                    (system-msg state :corp (str "gains 5 [Credits] and draws 1 card. Black Level Clearance is trashed"))
                                                    (trash state side card)
                                                    (effect-completed state side eid))))}
                               card nil))}}}

   "Breaker Bay Grid"
   {:events {:pre-rez-cost {:req (req (in-same-server? card target))
                            :effect (effect (rez-cost-bonus -5))}}}

   "Bryan Stinson"
   {:abilities [{:cost [:click 1]
                 :req (req (and (< (:credit runner) 6)
                                (< 0 (count (filter #(and (is-type? % "Operation")
                                                          (has-subtype? % "Transaction")) (:discard corp))))))
                 :label "Play a transaction operation from Archives ignoring all costs and remove it from the game"
                 :prompt "Choose a transaction operation to play"
                 :msg (msg "play " (:title target) " from Archives ignoring all costs and remove it from the game")
                 :choices (req (cancellable (filter #(and (is-type? % "Operation")
                                                          (has-subtype? % "Transaction")) (:discard corp)) :sorted))
                 :effect (effect (play-instant nil target {:ignore-cost true}) (move target :rfg))}]}

   "Calibration Testing"
   {:abilities [{:label "[Trash]: Place 1 advancement token on a card in this server"
                 :delayed-completion true
                 :effect (effect (continue-ability
                                   {:prompt "Select a card in this server"
                                    :choices {:req #(in-same-server? % card)}
                                    :delayed-completion true
                                    :msg (msg "place an advancement token on " (card-str state target))
                                    :effect (effect (add-prop target :advance-counter 1 {:placed true})
                                                    (trash eid card {:cause :ability-cost}))}
                                   card nil))}]}


   "Caprice Nisei"
   {:events {:pass-ice {:req (req (and this-server
                                       (= (:position run) 1))) ; trigger when last ice passed
                        :msg "start a Psi game"
                        :psi {:not-equal {:msg "end the run" :effect (effect (end-run))}}}
             :run {:req (req (and this-server
                                  (= (:position run) 0))) ; trigger on unprotected server
                   :msg "start a Psi game"
                   :psi {:not-equal {:msg "end the run" :effect (effect (end-run))}}}}
    :abilities [{:msg "start a Psi game"
                 :psi {:not-equal {:msg "end the run" :effect (effect (end-run))}}}]}

   "ChiLo City Grid"
   {:events {:successful-trace {:req (req this-server)
                                :delayed-completion true
                                :effect (effect (tag-runner :runner eid 1))
                                :msg "give the Runner 1 tag"}}}

   "Corporate Troubleshooter"
   {:abilities [{:label "[Trash]: Add strength to a rezzed ICE protecting this server" :choices :credit
                 :prompt "How many credits?"
                 :effect (req (let [boost target]
                                (resolve-ability
                                  state side
                                  {:choices {:req #(and (ice? %)
                                                        (rezzed? %))}
                                   :msg (msg "add " boost " strength to " (:title target))
                                   :effect (req (update! state side (assoc card :troubleshooter-target target
                                                                                :troubleshooter-amount boost))
                                                (trash state side (get-card state card))
                                                (update-ice-strength state side target))} card nil)))}]
    :events {:pre-ice-strength nil :runner-turn-ends nil :corp-turn-ends nil}
    :trash-effect
               {:effect (req (register-events
                               state side
                               (let [ct {:effect (req (unregister-events state side card)
                                                      (update! state side (dissoc card :troubleshooter-target))
                                                      (update-ice-strength state side (:troubleshooter-target card)))}]
                                 {:pre-ice-strength
                                                    {:req (req (= (:cid target) (:cid (:troubleshooter-target card))))
                                                     :effect (effect (ice-strength-bonus (:troubleshooter-amount card) target))}
                                  :runner-turn-ends ct :corp-turn-ends ct}) card))}}

   "Crisium Grid"
   (let [suppress-event {:req (req (and this-server (not= (:cid target) (:cid card))))}]
     {:suppress {:pre-successful-run suppress-event
                 :successful-run suppress-event}
      :events {:pre-successful-run {:silent (req true)
                                    :req (req this-server)
                                    :effect (req (swap! state update-in [:run :run-effect] dissoc :replace-access)
                                                 (swap! state update-in [:run] dissoc :successful)
                                                 (swap! state update-in [:runner :register :successful-run] #(next %)))}}})

   "Cyberdex Virus Suite"
   {:access {:delayed-completion true
             :effect (effect (show-wait-prompt :runner "Corp to use Cyberdex Virus Suite")
                             (continue-ability
                               {:optional {:prompt "Purge virus counters with Cyberdex Virus Suite?"
                                           :yes-ability {:msg (msg "purge virus counters")
                                                         :effect (effect (clear-wait-prompt :runner)
                                                                         (purge))}
                                           :no-ability {:effect (effect (clear-wait-prompt :runner))}}}
                               card nil))}
    :abilities [{:label "[Trash]: Purge virus counters"
                 :msg "purge virus counters" :effect (effect (trash card) (purge))}]}

   "Dedicated Technician Team"
   {:recurring 2}

   "Defense Construct"
   {:advanceable :always
    :abilities [{:label "[Trash]: Add 1 facedown card from Archives to HQ for each advancement token"
                 :req (req (and run (= (:server run) [:archives])
                                (pos? (get-in card [:advance-counter] 0))))
                 :effect (effect (resolve-ability
                                   {:show-discard true
                                    :choices {:max (get-in card [:advance-counter] 0)
                                              :req #(and (= (:side %) "Corp")
                                                         (not (:seen %))
                                                         (= (:zone %) [:discard]))}
                                              :msg (msg "add " (count targets) " facedown cards in Archives to HQ")
                                    :effect (req (doseq [c targets]
                                                   (move state side c :hand)))}
                                  card nil)
                                 (trash card))}]}

   "Disposable HQ"
   (letfn [(dhq [n i]
             {:req (req (pos? i))
              :prompt "Select a card in HQ to add to the bottom of R&D"
              :choices {:req #(and (= (:side %) "Corp")
                                   (in-hand? %))}
              :delayed-completion true
              :msg "add a card to the bottom of R&D"
              :effect (req (move state side target :deck)
                           (if (< n i)
                             (continue-ability state side (dhq (inc n) i) card nil)
                             (do
                               (clear-wait-prompt state :runner)
                               (effect-completed state side eid))))
              :cancel-effect (final-effect (clear-wait-prompt :runner))})]
     {:access {:delayed-completion true
               :effect (req (let [n (count (:hand corp))]
                              (show-wait-prompt state :runner "Corp to finish using Disposable HQ")
                              (continue-ability state side
                                {:optional
                                 {:prompt "Use Disposable HQ to add cards to the bottom of R&D?"
                                  :yes-ability {:delayed-completion true
                                                :msg "add cards in HQ to the bottom of R&D"
                                                :effect (effect (continue-ability (dhq 1 n) card nil))}
                                  :no-ability {:effect (effect (clear-wait-prompt :runner))}}}
                               card nil)))}})

   "Drone Screen"
   {:events {:run {:req (req (and this-server tagged))
                   :delayed-completion true
                   :trace {:base 3
                           :msg "do 1 meat damage"
                           :effect (effect (damage eid :meat 1 {:card card :unpreventable true}))}}}}

   "Experiential Data"
   {:effect (req (update-ice-in-server state side (card->server state card)))
    :events {:pre-ice-strength {:req (req (protecting-same-server? card target))
                                :effect (effect (ice-strength-bonus 1 target))}}
    :derez-effect {:effect (req (update-ice-in-server state side (card->server state card)))}
    :trash-effect {:effect (req (update-all-ice state side))}}

   "Expo Grid"
   (let [ability {:req (req (some #(and (is-type? % "Asset")
                                        (rezzed? %))
                                  (get-in corp (:zone card))))
                  :msg "gain 1 [Credits]"
                  :once :per-turn
                  :label "Gain 1 [Credits] (start of turn)"
                  :effect (effect (gain :credit 1))}]
   {:derezzed-events {:runner-turn-ends corp-rez-toast}
    :events {:corp-turn-begins ability}
    :abilities [ability]})

   "Fractal Threat Matrix"
   {:implementation "Manual trigger each time all subs are broken"
    :abilities [{:label "Trash the top 2 cards from the Stack"
                 :msg (msg (let [deck (:deck runner)]
                             (if (pos? (count deck))
                               (str "trash " (join ", " (map :title (take 2 deck))) " from the Stack")
                               "trash the top 2 cards from their Stack - but the Stack is empty")))
                 :effect (effect (mill :runner 2))}]}

   "Georgia Emelyov"
   {:events {:unsuccessful-run {:req (req (= (first (:server target)) (second (:zone card))))
                                :delayed-completion true
                                :msg "do 1 net damage"
                                :effect (effect (damage eid :net 1 {:card card}))}}
    :abilities [{:cost [:credit 2]
                 :label "Move to another server"
                 :delayed-completion true
                 :effect (effect (continue-ability
                                   {:prompt "Choose a server"
                                    :choices (butlast (server-list state side))
                                    :msg (msg "move to " target)
                                    :effect (req (let [c (move state side card
                                                               (conj (server->zone state target) :content))]
                                                   (unregister-events state side card)
                                                   (register-events state side (:events (card-def c)) c)))}
                                   card nil))}]}

   "Heinlein Grid"
   {:abilities [{:req (req this-server)
                 :label "Force the Runner to lose all [Credits] from spending or losing a [Click]"
                 :msg (msg "force the Runner to lose all " (:credit runner) " [Credits]") :once :per-run
                 :effect (effect (lose :runner :credit :all :run-credit :all))}]}

   "Helheim Servers"
   {:abilities [{:label "Trash 1 card from HQ: All ice protecting this server has +2 strength until the end of the run"
                 :req (req (and this-server (pos? (count run-ices)) (pos? (count (:hand corp)))))
                 :delayed-completion true
                 :effect (req (show-wait-prompt state :runner "Corp to use Helheim Servers")
                              (when-completed
                                (resolve-ability
                                  state side
                                  {:prompt "Choose a card in HQ to trash"
                                   :choices {:req #(and (in-hand? %) (= (:side %) "Corp"))}
                                   :effect (effect (trash target) (clear-wait-prompt :runner))} card nil)
                                (do (register-events
                                      state side
                                      {:pre-ice-strength {:req (req (= (card->server state card)
                                                                       (card->server state target)))
                                                          :effect (effect (ice-strength-bonus 2 target))}
                                       :run-ends {:effect (effect (unregister-events card))}} card)
                                    (continue-ability
                                      state side
                                      {:effect (req (update-ice-in-server
                                                      state side (card->server state card)))} card nil))))}]
    :events {:pre-ice-strength nil}}

   "Henry Phillips"
   {:implementation "Manually triggered by Corp"
    :abilities [{:req (req (and this-server tagged))
                 :msg "gain 2 [Credits]"
                 :effect (effect (gain :credit 2))}]}

   "Hokusai Grid"
   {:events {:successful-run {:req (req this-server) :msg "do 1 net damage"
                              :delayed-completion true
                              :effect (effect (damage eid :net 1 {:card card}))}}}

   "Keegan Lane"
   {:abilities [{:label "[Trash], remove a tag: Trash a program"
                 :req (req (and this-server
                                (pos? (get-in @state [:runner :tag]))
                                (not (empty? (filter #(is-type? % "Program")
                                                     (all-installed state :runner))))))
                 :msg (msg "remove 1 tag")
                 :effect (req (resolve-ability state side trash-program card nil)
                              (trash state side card {:cause :ability-cost})
                              (lose state :runner :tag 1))}]}

   "Khondi Plaza"
   {:recurring (effect (set-prop card :rec-counter (count (get-remotes @state))))
    :effect (effect (set-prop card :rec-counter (count (get-remotes @state))))}

   "K. P. Lynn"
   (let [abi {:prompt "Choose one"
              :player :runner
              :choices ["Take 1 tag" "End the run"]
              :effect (req (if (= target "Take 1 tag")
                             (do (tag-runner state :runner 1)
                                 (system-msg state :corp (str "uses K. P. Lynn. Runner chooses to take 1 tag")))
                             (do (end-run state side)
                                 (system-msg state :corp (str "uses K. P. Lynn. Runner chooses to end the run")))))}]
     {:events {:pass-ice {:req (req (and this-server (= (:position run) 1))) ; trigger when last ice passed
                          :delayed-completion true
                          :effect (req (continue-ability state :runner abi card nil))}
               :run {:req (req (and this-server (= (:position run) 0))) ; trigger on unprotected server
                     :delayed-completion true
                     :effect (req (continue-ability state :runner abi card nil))}}})

   "Manta Grid"
   {:events {:successful-run-ends
             {:msg "gain a [Click] next turn"
              :req (req (and (= (first (:server target)) (second (:zone card)))
                             (or (< (:credit runner) 6) (zero? (:click runner)))))
              :effect (req (swap! state update-in [:corp :extra-click-temp] (fnil inc 0)))}}}

   "Marcus Batty"
   {:abilities [{:req (req this-server)
                 :label "[Trash]: Start a Psi game"
                 :msg "start a Psi game"
                 :psi {:not-equal {:prompt "Select a rezzed piece of ICE to resolve one of its subroutines"
                                   :choices {:req #(and (ice? %)
                                                        (rezzed? %))}
                                   :msg (msg "resolve a subroutine on " (:title target))}}
                 :effect (effect (trash card))}]}

   "Mason Bellamy"
   {:implementation "Manually triggered by Corp"
    :abilities [{:label "Force the Runner to lose [Click] after an encounter where they broke a subroutine"
                 :req (req this-server)
                 :msg "force the Runner to lose [Click]"
                 :effect (effect (lose :runner :click 1))}]}

   "Midori"
   {:abilities
    [{:req (req this-server)
      :label "Swap the ICE being approached with a piece of ICE from HQ"
      :prompt "Select a piece of ICE"
      :choices {:req #(and (ice? %)
                           (in-hand? %))}
      :once :per-run
      :msg (msg "swap " (card-str state current-ice) " with a piece of ICE from HQ")
      :effect (req (let [hqice target
                         c current-ice]
                     (resolve-ability state side
                       {:effect (req (let [newice (assoc hqice :zone (:zone c))
                                           cndx (ice-index state c)
                                           ices (get-in @state (cons :corp (:zone c)))
                                           newices (apply conj (subvec ices 0 cndx) newice (subvec ices cndx))]
                                       (swap! state assoc-in (cons :corp (:zone c)) newices)
                                       (swap! state update-in [:corp :hand]
                                              (fn [coll] (remove-once #(not= (:cid %) (:cid hqice)) coll)))
                                       (trigger-event state side :corp-install newice)
                                       (move state side c :hand)))} card nil)))}]}

   "Mumbad City Grid"
   {:abilities [{:req (req this-server)
                 :label "Swap the ICE just passed with another piece of ICE protecting this server"
                 :effect (req (let [passed-ice (nth (get-in @state (vec (concat [:corp :servers] (:server run) [:ices])))
                                                                                (:position run))
                                    ice-zone (:zone passed-ice)]
                                 (resolve-ability state :corp
                                   {:prompt (msg "Select a piece of ICE to swap with " (:title passed-ice))
                                    :choices {:req #(and (= ice-zone (:zone %)) (ice? %))}
                                    :effect (req (let [fndx (ice-index state passed-ice)
                                                       sndx (ice-index state target)
                                                       fnew (assoc passed-ice :zone (:zone target))
                                                       snew (assoc target :zone (:zone passed-ice))]
                                                   (swap! state update-in (cons :corp ice-zone)
                                                          #(assoc % fndx snew))
                                                   (swap! state update-in (cons :corp ice-zone)
                                                          #(assoc % sndx fnew))
                                                   (update-ice-strength state side fnew)
                                                   (update-ice-strength state side snew)))} card nil)
                                 (system-msg state side (str "uses Mumbad City Grid to swap " (card-str state passed-ice)
                                                             " with " (card-str state target)))))}]}

   "Mumbad Virtual Tour"
   {:implementation "Only forces trash if runner has no Imps and enough credits in the credit pool"
    :flags {:must-trash true}
    :access {:req (req installed)
             :effect (req (let [trash-cost (trash-cost state side card)
                                no-salsette (remove #(= (:title %) "Salsette Slums") (all-active state :runner))
                                slow-trash (any-flag-fn? state :runner :slow-trash true no-salsette)]
                            (if (and (can-pay? state :runner nil :credit trash-cost)
                                     (not slow-trash))
                              (do (toast state :runner "You have been forced to trash Mumbad Virtual Tour" "info")
                                  (swap! state assoc-in [:runner :register :force-trash] true))
                              (toast state :runner
                                     (str "You must trash Mumbad Virtual Tour, if able, using any available means "
                                          "(Whizzard, Imp, Ghost Runner, Net Celebrity...)")))))}
    :trash-effect {:when-inactive true
                   :effect (req (swap! state assoc-in [:runner :register :force-trash] false))}}

   "NeoTokyo Grid"
   (let [ng {:req (req (in-same-server? card target))
             :once :per-turn
             :msg "gain 1 [Credits]" :effect (effect (gain :credit 1))}]
     {:events {:advance ng :advancement-placed ng}})

   "Nihongai Grid"
   {:events
    {:successful-run
     {:interactive (req true)
      :delayed-completion true
      :req (req (and this-server
                     (or (< (:credit runner) 6)
                         (< (count (:hand runner)) 2))
                     (not-empty (:hand corp))))
      :effect (req (show-wait-prompt state :runner "Corp to use Nihongai Grid")
                   (let [top5 (take 5 (:deck corp))]
                     (if (pos? (count top5))
                       (continue-ability state side
                         {:optional
                          {:prompt "Use Nihongai Grid to look at top 5 cards of R&D and swap one with a card from HQ?"
                           :yes-ability
                           {:delayed-completion true
                            :prompt "Choose a card to swap with a card from HQ"
                            :choices top5
                            :effect (req (let [rdc target]
                                           (continue-ability state side
                                             {:delayed-completion true
                                              :prompt (msg "Choose a card in HQ to swap for " (:title rdc))
                                              :choices {:req in-hand?}
                                              :msg "swap a card from the top 5 of R&D with a card in HQ"
                                              :effect (req (let [hqc target
                                                                 newrdc (assoc hqc :zone [:deck])
                                                                 deck (vec (get-in @state [:corp :deck]))
                                                                 rdcndx (first (keep-indexed #(when (= (:cid %2) (:cid rdc)) %1) deck))
                                                                 newdeck (seq (apply conj (subvec deck 0 rdcndx) target (subvec deck rdcndx)))]
                                                             (swap! state assoc-in [:corp :deck] newdeck)
                                                             (swap! state update-in [:corp :hand]
                                                                    (fn [coll] (remove-once #(not= (:cid %) (:cid hqc)) coll)))
                                                             (move state side rdc :hand)
                                                             (clear-wait-prompt state :runner)
                                                             (effect-completed state side eid)))}
                                            card nil)))}
                           :no-ability {:effect (req (clear-wait-prompt state :runner)
                                                     (effect-completed state side eid card))}}}
                        card nil)
                       (do (clear-wait-prompt state :runner)
                           (effect-completed state side eid card)))))}}}

   "Oaktown Grid"
   {:events {:pre-trash {:req (req (in-same-server? card target))
                         :effect (effect (trash-cost-bonus 3))}}}

   "Oberth Protocol"
   {:additional-cost [:forfeit]
    :events {:advance {:req (req (and (same-server? card target)
                                      (empty? (filter #(= (second (:zone %)) (second (:zone card)))
                                                      (map first (turn-events state side :advance))))))
                       :msg (msg "place an additional advancement token on " (card-str state target))
                       :effect (effect (add-prop :corp target :advance-counter 1 {:placed true}))}}}

   "Off the Grid"
   {:implementation "Installation restriction not enforced"
    :effect (req (prevent-run-on-server state card (second (:zone card))))
    :events {:runner-turn-begins {:effect (req (prevent-run-on-server state card (second (:zone card))))}
             :successful-run {:req (req (= target :hq))
                              :effect (req (trash state :corp card)
                                           (enable-run-on-server state card
                                                                 (second (:zone card)))
                                           (system-msg state :corp (str "trashes Off the Grid")))}}
    :leave-play (req (enable-run-on-server state card (second (:zone card))))}

   "Old Hollywood Grid"
   (let [ohg {:req (req (or (in-same-server? card target)
                            (from-same-server? card target)))
              :effect (effect (register-persistent-flag!
                                card :can-steal
                                (fn [state _ card]
                                  (if-not (some #(= (:title %) (:title card)) (:scored runner))
                                    ((constantly false)
                                      (toast state :runner "Cannot steal due to Old Hollywood Grid." "warning"))
                                    true))))}]
     {:trash-effect
              {:req (req (and (= :servers (first (:previous-zone card))) (:run @state)))
               :effect (effect (register-events {:pre-steal-cost (assoc ohg :req (req (or (= (:zone (get-nested-host target)) (:previous-zone card))
                                                                                          (= (central->zone (:zone target))
                                                                                             (butlast (:previous-zone card))))))
                                                 :run-ends {:effect (effect (unregister-events card))}}
                                                (assoc card :zone '(:discard))))}
      :events {:pre-steal-cost ohg
               :post-access-card {:effect (effect (clear-persistent-flag! target :can-steal))}}})

   "Panic Button"
   {:init {:root "HQ"} :abilities [{:cost [:credit 1] :label "Draw 1 card" :effect (effect (draw))
                                    :req (req (and run (= (first (:server run)) :hq)))}]}

   "Port Anson Grid"
   {:msg "prevent the Runner from jacking out unless they trash an installed program"
    :effect (req (when this-server
                   (prevent-jack-out state side)))
    :events {:run {:req (req this-server)
                   :msg "prevent the Runner from jacking out unless they trash an installed program"
                   :effect (effect (prevent-jack-out))}
             :runner-trash {:req (req (and this-server (is-type? target "Program")))
                            :effect (req (swap! state update-in [:run] dissoc :cannot-jack-out))}}}

   "Prisec"
   {:access {:req (req (installed? card))
             :delayed-completion true
             :effect (effect (show-wait-prompt :runner "Corp to use Prisec")
                             (continue-ability
                               {:optional
                                {:prompt "Pay 2 [Credits] to use Prisec ability?"
                                 :end-effect (effect (clear-wait-prompt :runner))
                                 :yes-ability {:cost [:credit 2]
                                               :msg "do 1 meat damage and give the Runner 1 tag"
                                               :delayed-completion true
                                               :effect (req (when-completed (damage state side :meat 1 {:card card})
                                                                            (tag-runner state :runner eid 1)))}}}
                               card nil))}}

   "Product Placement"
   {:access {:req (req (not= (first (:zone card)) :discard))
             :msg "gain 2 [Credits]" :effect (effect (gain :corp :credit 2))}}

   "Red Herrings"
   (let [ab {:req (req (or (in-same-server? card target)
                           (from-same-server? card target)))
             :effect (effect (steal-cost-bonus [:credit 5]))}]
     {:trash-effect
      {:req (req (and (= :servers (first (:previous-zone card))) (:run @state)))
       :effect (effect (register-events {:pre-steal-cost (assoc ab :req (req (or (= (:zone target) (:previous-zone card))
                                                                                 (= (central->zone (:zone target))
                                                                                    (butlast (:previous-zone card))))))
                                         :run-ends {:effect (effect (unregister-events card))}}
                                        (assoc card :zone '(:discard))))}
      :events {:pre-steal-cost ab :run-ends nil}})

   "Research Station"
   {:init {:root "HQ"}
    :in-play [:hand-size-modification 2]}

   "Ruhr Valley"
   {:events {:run {:req (req this-server)
                   :effect (effect (lose :runner :click 1))
                   :msg "force the Runner to spend an additional [Click]"}
             :runner-turn-begins {:req (req (> (:click-per-turn runner) 1))
                                  :effect (req (enable-run-on-server state card (second (:zone card))))}
             :runner-spent-click {:req (req (<= 1 (:click runner)))
                                  :effect (req (prevent-run-on-server state card (second (:zone card))))}
             :leave-play (req (enable-run-on-server state card (second (:zone card))))}}

   "Rutherford Grid"
   {:events {:pre-init-trace {:req (req this-server)
                              :effect (effect (init-trace-bonus 2))}}}

   "Ryon Knight"
   {:abilities [{:label "[Trash]: Do 1 brain damage"
                 :msg "do 1 brain damage" :req (req (and this-server (zero? (:click runner))))
                 :delayed-completion true
                 :effect (effect (trash card) (damage eid :brain 1 {:card card}))}]}

   "SanSan City Grid"
   {:effect (req (when-let [agenda (some #(when (is-type? % "Agenda") %)
                                         (:content (card->server state card)))]
                   (update-advancement-cost state side agenda)))
    :events {:corp-install {:req (req (and (is-type? target "Agenda")
                                           (in-same-server? card target)))
                            :effect (effect (update-advancement-cost target))}
             :pre-advancement-cost {:req (req (in-same-server? card target))
                                    :effect (effect (advancement-cost-bonus -1))}}}

   "Satellite Grid"
   {:effect (req (doseq [c (:ices (card->server state card))]
                   (set-prop state side c :extra-advance-counter 1))
                 (update-all-ice state side))
    :events {:corp-install {:req (req (and (ice? target)
                                           (protecting-same-server? card target)))
                            :effect (effect (set-prop target :extra-advance-counter 1))}}
    :leave-play (req (doseq [c (:ices (card->server state card))]
                       (update! state side (dissoc c :extra-advance-counter)))
                     (update-all-ice state side))}

   "Self-destruct"
   {:abilities [{:req (req this-server)
                 :label "[Trash]: Trace X - Do 3 net damage"
                 :effect (req (let [serv (card->server state card)
                                    cards (concat (:ices serv) (:content serv))]
                                (trash state side card)
                                (doseq [c cards] (trash state side c))
                                (resolve-ability
                                  state side
                                  {:trace {:base (req (dec (count cards)))
                                           :effect (effect (damage eid :net 3 {:card card}))
                                           :msg "do 3 net damage"}} card nil)))}]}

   "Shell Corporation"
   {:abilities
    [{:cost [:click 1]
      :msg "store 3 [Credits]" :once :per-turn
      :effect (effect (add-counter card :credit 3))}
     {:cost [:click 1]
      :msg (msg "gain " (get-in card [:counter :credit] 0) " [Credits]") :once :per-turn
      :label "Take all credits"
      :effect (effect (gain :credit (get-in card [:counter :credit] 0))
                      (set-prop card :counter {:credit 0}))}]}

   "Signal Jamming"
   {:abilities [{:label "[Trash]: Cards cannot be installed until the end of the run"
                 :msg (msg "prevent cards being installed until the end of the run")
                 :req (req this-server)
                 :effect (effect (trash (get-card state card) {:cause :ability-cost}))}]
    :trash-effect {:effect (effect (lock-install (:cid card) :runner)
                                   (lock-install (:cid card) :corp)
                                   (toast :runner "Cannot install until the end of the run")
                                   (toast :corp "Cannot install until the end of the run")
                                   (register-events {:run-ends {:effect (effect (unlock-install (:cid card) :runner)
                                                                                (unlock-install (:cid card) :corp))}}
                                                    (assoc card :zone '(:discard))))}
    :events {:run-ends nil
             :turn-ends {:effect (effect (unregister-events card))}}}

   "Simone Diego"
   {:recurring 2}

   "Strongbox"
   (let [ab {:req (req (or (in-same-server? card target)
                           (from-same-server? card target)))
             :effect (effect (steal-cost-bonus [:click 1]))}]
     {:trash-effect
      {:req (req (and (= :servers (first (:previous-zone card))) (:run @state)))
       :effect (effect (register-events {:pre-steal-cost (assoc ab :req (req (or (= (:zone target) (:previous-zone card))
                                                                                 (= (central->zone (:zone target))
                                                                                    (butlast (:previous-zone card))))))
                                         :run-ends {:effect (effect (unregister-events card))}}
                                        (assoc card :zone '(:discard))))}
      :events {:pre-steal-cost ab :run-ends nil}})

   "Surat City Grid"
   {:events
    {:rez {:req (req (and (same-server? card target)
                          (not (and (is-type? target "Upgrade")
                                    (is-central? (second (:zone target)))))
                          (not= (:cid target) (:cid card))
                          (seq (filter #(and (not (rezzed? %))
                                             (not (is-type? % "Agenda"))) (all-installed state :corp)))))
           :effect (effect (resolve-ability
                             {:optional
                              {:prompt (msg "Rez another card with Surat City Grid?")
                               :yes-ability {:prompt "Select a card to rez"
                                             :choices {:req #(and (not (rezzed? %))
                                                                  (not (is-type? % "Agenda")))}
                                             :msg (msg "rez " (:title target) ", lowering the rez cost by 2 [Credits]")
                                             :effect (effect (rez-cost-bonus -2)
                                                             (rez target))}}}
                            card nil))}}}

   "The Twins"
   {:abilities [{:label "Reveal and trash a copy of the ICE just passed from HQ"
                 :req (req (and this-server
                                (> (count (get-run-ices state)) (:position run))
                                (:rezzed (get-in (:ices (card->server state card)) [(:position run)]))))
                 :effect (req (let [icename (:title (get-in (:ices (card->server state card)) [(:position run)]))]
                                (resolve-ability
                                  state side
                                  {:prompt "Select a copy of the ICE just passed"
                                   :choices {:req #(and (in-hand? %)
                                                        (ice? %)
                                                        (= (:title %) icename))}
                                   :effect (req (trash state side (assoc target :seen true))
                                                (swap! state update-in [:run]
                                                       #(assoc % :position (inc (:position run)))))
                                   :msg (msg "trash a copy of " (:title target) " from HQ and force the Runner to encounter it again")}
                                 card nil)))}]}

   "Tori Hanzō"
   {:events
    {:pre-resolve-damage
     {:once :per-run
      :delayed-completion true
      :req (req (and this-server (= target :net) (> (last targets) 0) (can-pay? state :corp nil [:credit 2])))
      :effect (req (swap! state assoc-in [:damage :damage-replace] true)
                   (damage-defer state side :net (last targets))
                   (show-wait-prompt state :runner "Corp to use Tori Hanzō")
                   (continue-ability state side
                     {:optional {:prompt (str "Pay 2 [Credits] to do 1 brain damage with Tori Hanzō?") :player :corp
                                 :yes-ability {:delayed-completion true
                                               :msg "do 1 brain damage instead of net damage"
                                               :effect (req (swap! state update-in [:damage] dissoc :damage-replace :defer-damage)
                                                            (clear-wait-prompt state :runner)
                                                            (pay state :corp card :credit 2)
                                                            (when-completed (damage state side :brain 1 {:card card})
                                                                            (do (swap! state assoc-in [:damage :damage-replace] true)
                                                                                (effect-completed state side eid))))}
                                 :no-ability {:delayed-completion true
                                              :effect (req (swap! state update-in [:damage] dissoc :damage-replace)
                                                           (clear-wait-prompt state :runner)
                                                           (effect-completed state side eid))}}} card nil))}
     :prevented-damage {:req (req (and this-server (= target :net) (> (last targets) 0)))
                        :effect (req (swap! state assoc-in [:per-run (:cid card)] true))}}}

   "Traffic Analyzer"
   {:events {:rez {:req (req (and (protecting-same-server? card target)
                                  (ice? target)))
                   :interactive (req true)
                   :trace {:base 2
                           :msg "gain 1 [Credits]"
                           :effect (effect (gain :credit 1))}}}}

   "Tyrs Hand"
   {:abilities [{:label "[Trash]: Prevent a subroutine on a piece of Bioroid ICE from being broken"
                 :req (req (and (= (butlast (:zone current-ice)) (butlast (:zone card)))
                                (has-subtype? current-ice "Bioroid")))
                 :effect (effect (trash card))
                 :msg (msg "prevent a subroutine on " (:title current-ice) " from being broken")}]}

   "Underway Grid"
   {:implementation "Bypass prevention is not implemented"
    :events {:pre-expose {:req (req (same-server? card target))
                          :msg "prevent 1 card from being exposed"
                          :effect (effect (expose-prevent 1))}}}

   "Valley Grid"
   {:implementation "Activation is manual"
    :abilities [{:req (req this-server)
                 :label "Reduce Runner's maximum hand size by 1 until start of next Corp turn"
                 :msg "reduce the Runner's maximum hand size by 1 until the start of the next Corp turn"
                 :effect (req (update! state side (assoc card :times-used (inc (get card :times-used 0))))
                              (lose state :runner :hand-size-modification 1))}]
    :trash-effect {:req (req (and (= :servers (first (:previous-zone card))) (:run @state)))
                   :effect (req (when-let [n (:times-used card)]
                                  (register-events state side
                                                   {:corp-turn-begins
                                                    {:msg (msg "increase the Runner's maximum hand size by " n)
                                                     :effect (effect (gain :runner :hand-size-modification n)
                                                                     (unregister-events card)
                                                                     (update! (dissoc card :times-used)))}}
                                                   (assoc card :zone '(:discard)))))}
    :events {:corp-turn-begins {:req (req (:times-used card))
                                :msg (msg "increase the Runner's maximum hand size by "
                                          (:times-used card))
                                :effect (effect (gain :runner :hand-size-modification
                                                      (:times-used card))
                                                (update! (dissoc card :times-used)))}}}

   "Warroid Tracker"
   (letfn [(wt [card n t]
             {:prompt "Choose an installed card to trash due to Warroid Tracker"
              :delayed-completion true
              :player :runner
              :priority 2
              :choices {:req #(and (installed? %) (= (:side %) "Runner"))}
              :effect (req (system-msg state side (str "trashes " (card-str state target) " due to Warroid Tracker"))
                           (trash state side target {:unpreventable true})
                           (if (> n t)
                             (continue-ability state side (wt card n (inc t)) card nil)
                             (do (clear-wait-prompt state :corp)
                                 (effect-completed state side eid card)))
                           ;; this ends-the-run if WT is the only card and is trashed, and trashes at least one runner card
                           (when (zero? (count (cards-to-access state side (get-in @state [:run :server]))))
                             (handle-end-run state side)))})]
   {:implementation "Does not handle UFAQ interaction with Singularity"
    :events {:runner-trash {:delayed-completion true
                            :req (req (= (-> card :zone second) (-> target :zone second)))
                            :trace {:base 4
                                    :effect (req (let [n (count (all-installed state :runner))
                                                       n (if (> n 2) 2 n)]
                                                   (if (pos? n) (do (system-msg state side (str "uses Warroid Tracker to force the runner to trash " n " installed card(s)"))
                                                                    (show-wait-prompt state :corp "Runner to choose cards to trash")
                                                                    (resolve-ability state side (wt card n 1) card nil))
                                                                (system-msg state side (str "uses Warroid Tracker but there are no installed cards to trash")))))}}}})

   "Will-o-the-Wisp"
   {:events
    {:successful-run
     {:interactive (req true)
      :delayed-completion true
      :req (req (and this-server
                     (some #(has-subtype? % "Icebreaker") (all-installed state :runner))))
      :effect (req (show-wait-prompt state :runner "Corp to use Will-o'-the-Wisp")
                   (continue-ability state side
                     {:optional
                      {:prompt "Trash Will-o'-the-Wisp?"
                       :choices {:req #(has-subtype? % "Icebreaker")}
                       :yes-ability {:delayed-completion true
                                     :prompt "Choose an icebreaker used to break at least 1 subroutine during this run"
                                     :choices {:req #(has-subtype? % "Icebreaker")}
                                     :msg (msg "add " (:title target) " to the bottom of the Runner's Stack")
                                     :effect (effect (trash card)
                                                     (move :runner target :deck)
                                                     (clear-wait-prompt :runner)
                                                     (effect-completed eid card))}
                       :no-ability {:effect (effect (clear-wait-prompt :runner)
                                                    (effect-completed eid card))}}}
                    card nil))}}}})
