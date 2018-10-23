(ns asgnx.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!!]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check.generators :as gen]
			[asgnx.core :refer :all]
			[asgnx.parser :as parser]
			[asgnx.kvstore :as kvstore :refer [put! get!]]
			[asgnx.actions :as actions]
			[asgnx.commands.report :as report]
	)
)


(deftest words-test
  (testing "that sentences can be split into their constituent words"
    (is (= ["a" "b" "c"] (parser/words "a b c")))
    (is (= [] (parser/words "   ")))
    (is (= [] (parser/words nil)))
    (is (= ["a"] (parser/words "a")))
    (is (= ["a"] (parser/words "a ")))
    (is (= ["a" "b"] (parser/words "a b")))))


(deftest cmd-test
  (testing "that commands can be parsed from text messages"
    (is (= "foo" (parser/cmd "foo")))
    (is (= "foo" (parser/cmd "foo x y")))
    (is (= nil   (parser/cmd nil)))
    (is (= ""    (parser/cmd "")))))


(deftest args-test
  (testing "that arguments can be parsed from text messages"
    (is (= ["x" "y"] (parser/args "foo x y")))
    (is (= ["x"] (parser/args "foo x")))
    (is (= [] (parser/args "foo")))
    (is (= [] (parser/args nil)))))


(deftest parsed-msg-test
  (testing "that text messages can be parsed into cmd/args data structures"
    (is (= {:cmd "foo"
            :args ["x" "y"]}
           (parser/parsed-msg "foo x y")))
    (is (= {:cmd "foo"
            :args ["x"]}
           (parser/parsed-msg "foo x")))
    (is (= {:cmd "foo"
            :args []}
           (parser/parsed-msg "foo")))
    (is (= {:cmd "foo"
            :args ["x" "y" "z" "somereallylongthing"]}
           (parser/parsed-msg "foo x y z somereallylongthing")))))



(deftest create-router-test
  (testing "correct creation of a function to lookup a handler for a parsed message"
    (let [router (create-router {"hello" #(str (:cmd %) " " "test")
                                 "argc"  #(count (:args %))
                                 "echo"  identity
                                 "default" (fn [& a] "No!")})
          msg1   {:cmd "hello"}
          msg2   {:cmd "argc" :args [1 2 3]}
          msg3   {:cmd "echo" :args ["a" "z"]}
          msg4   {:cmd "echo2" :args ["a" "z"]}]
      (is (= "hello test" ((router msg1) msg1)))
      (is (= "No!" ((router msg4) msg4)))
      (is (= 3 ((router msg2) msg2)))
      (is (= msg3 ((router msg3) msg3))))))


(deftest action-send-msg-test
  (testing "That action send msg returns a correctly formatted map"
    (is (= :send
           (:action (actions/send-msg :bob "foo"))))
    (is (= :bob
           (:to (actions/send-msg :bob "foo"))))
    (is (= "foo"
           (:msg (actions/send-msg [:a :b] "foo"))))))


(deftest action-send-msgs-test
  (testing "That action send msgs generates a list of sends"
    (let [a (actions/send-msg [:a :f :b] 1)
          b (actions/send-msg [:a :f :d] 1)
          c (actions/send-msg [:a :f :e] 1)
          d (actions/send-msg [:a :f :c] 1)]
      (is (= [a b c d]
             (actions/send-msgs [[:a :f :b]
                                [:a :f :d]
                                [:a :f :e]
                                [:a :f :c]]
                              1))))))

(deftest action-insert-test
  (testing "That action insert returns a correctly formatted map"
    (is (= #{:action :ks :v}
           (into #{}(keys (actions/insert [:a :b] {:foo 1})))))
    (is (= #{:assoc-in [:a :b] {:foo 1}}
           (into #{}(vals (actions/insert [:a :b] {:foo 1})))))
    (is (= :assoc-in
           (:action (actions/insert [:a :b] {:foo 1}))))
    (is (= {:foo 1}
           (:v (actions/insert [:a :b] {:foo 1}))))
    (is (= [:a :b]
           (:ks (actions/insert [:a :b] {:foo 1}))))))


(deftest action-remove-test
  (testing "That action remove returns a correctly formatted map"
    (is (= #{:action :ks}
         (into #{} (keys (actions/delete [:a :b])))))
    (is (= #{:dissoc-in [:a :b]}
          (into #{}(vals (actions/delete [:a :b])))))
    (is (= :dissoc-in
           (:action (actions/delete [:a :b]))))
    (is (= [:a :b]
           (:ks (actions/delete [:a :b]))))))


(deftest action-inserts-test
  (testing "That action inserts generates a list of inserts"
    (let [a (actions/insert [:a :f :b] 1)
          b (actions/insert [:a :f :d] 1)
          c (actions/insert [:a :f :e] 1)
          d (actions/insert [:a :f :c] 1)]
      (is (= [a b c d]
             (actions/inserts [:a :f] [:b :d :e :c] 1))))))


(defn action-send [system {:keys [to msg]}]
  (put! (:state-mgr system) [:msgs to] msg))

(defn pending-send-msgs [system to]
  (get! (:state-mgr system) [:msgs to]))

(def send-action-handlers
  {:send action-send})

(defn print-locations [system]
	(println "\nRAAAAAAAAAAAAYEEEEEYAAAAAA")
	(println (<!! (get! (:state-mgr system) [:locations])))
	(println "RAAAAAAAAAAAAYEEEEEYAAAAAA")
	)
(defn get-locations [system]
		(<!! (get! (:state-mgr system) [:locations]))
	)

(deftest handle-message-test
  (testing "the integration and handling of messages"
    (let [ehdlrs (merge
                   send-action-handlers
                   kvstore/action-handlers)
          state  (atom {})
          smgr   (kvstore/create state)
          system {:state-mgr smgr
				  :effect-handlers ehdlrs}]
				
		(is (= "You are not a registered user of this application."
             (<!! (handle-message
                    system
                    "test-user"
					"unregister"))))
		

		(is (= "No wait times have been reported."
             (<!! (handle-message
                    system
                    "test-user"
					"wait"))))

		(is (= "usage: report <location> <time in minutes>"
             (<!! (handle-message
                    system
                    "test-user"
					"report"))))

		(is (= "usage: report <location> <time in minutes>"
             (<!! (handle-message
                    system
                    "test-user"
					"report rand"))))

		(is (= report/invalid-location-msg
             (<!! (handle-message
                    system
                    "test-user"
					"report banana 15"))))
		
		(is (= "Successfully recorded wait time. Thanks :)"
             (<!! (handle-message
                    system
                    "test-user"
					"report rand 15"))))

		(is (= (get-locations system)
				{"rand" 15}
			))

		(is (= "Successfully recorded wait time. Thanks :)"
             (<!! (handle-message
                    system
                    "test-user"
					"report rand 20"))))

		(is (= (get-locations system)
				{"rand" 20}
			))

		(is (= "Successfully recorded wait time. Thanks :)"
             (<!! (handle-message
                    system
                    "test-user"
					"report pub 20"))))

		(is (= (get-locations system)
				{"rand" 20 "pub" 20}
			))

		(is (= "rand: 20min\npub: 20min"
             (<!! (handle-message
                    system
                    "test-user"
					"wait"))))

		(is (= "Successfully recorded wait time. Thanks :)"
             (<!! (handle-message
                    system
                    "test-user"
					"report grins 15"))))

		(is (= (get-locations system)
				{"rand" 20 "pub" 20 "grins" 15}
			))

		(is (= "rand: 20min\npub: 20min\ngrins: 15min"
             (<!! (handle-message
                    system
                    "test-user"
					"wait"))))

		(is (= "rand: 20min\npub: 20min\ngrins: 15min"
             (<!! (handle-message
                    system
                    "test-user"
					"wait"))))

		(print-locations system)

	)
	)
)

;       (is (= "There are no experts on that topic."
;              (<!! (handle-message
;                     system
;                     "test-user"
;                     "ask food best burger in nashville"))))
;       (is (= "test-user is now an expert on food."
;              (<!! (handle-message
;                     system
;                     "test-user"
;                     "expert food"))))
;       (is (= "Asking 1 expert(s) for an answer to: \"what burger\""
;              (<!! (handle-message
;                     system
;                     "test-user"
;                     "ask food what burger"))))
;       (is (= "what burger"
;              (<!! (pending-send-msgs system "test-user"))))
;       (is (= "test-user2 is now an expert on food."
;              (<!! (handle-message
;                     system
;                     "test-user2"
;                     "expert food"))))
;       (is (= "Asking 2 expert(s) for an answer to: \"what burger\""
;              (<!! (handle-message
;                     system
;                     "test-user"
;                     "ask food what burger"))))
;       (is (= "what burger"
;              (<!! (pending-send-msgs system "test-user"))))
;       (is (= "what burger"
;              (<!! (pending-send-msgs system "test-user2"))))
;       (is (= "You must ask a valid question."
;              (<!! (handle-message
;                     system
;                     "test-user"
;                     "ask food "))))
;       (is (= "test-user is now an expert on nashville."
;              (<!! (handle-message
;                     system
;                     "test-user"
;                     "expert nashville"))))
;       (is (= "Asking 1 expert(s) for an answer to: \"what bus\""
;              (<!! (handle-message
;                     system
;                     "test-user2"
;                     "ask nashville what bus"))))
;       (is (= "what bus"
;              (<!! (pending-send-msgs system "test-user"))))
;       (is (= "Your answer was sent."
;              (<!! (handle-message
;                    system
;                    "test-user"
;                    "answer the blue bus"))))
;       (is (= "the blue bus"
;              (<!! (pending-send-msgs system "test-user2"))))
;       (is (= "You did not provide an answer."
;              (<!! (handle-message
;                    system
;                    "test-user"
;                    "answer"))))
;       (is (= "You haven't been asked a question."
;              (<!! (handle-message
;                    system
;                    "test-user3"
; 				   "answer the blue bus"))))