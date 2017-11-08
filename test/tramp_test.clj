(ns tramp-test
  (:require [clojure.test :refer :all]
            [tramp :refer :all]))

(deftest test-jump
  (let [j (jump inc [1] str)]
    (testing "Result of calling jump is a function, with correct metadata"
      (is (fn? j))
      (is (= inc (:fn (meta j))))
      (is (= [1] (:args (meta j)))))
    (testing "Calling function with 0 args returns (f args) passed into next-fn"
      (is (= "2" (j))))
    (testing "Calling function with 1 override arg returns the override passed into next-fn"
      (is (= "3" (j 3))))))

(deftest test-form->function
  (testing "single iFN returned as-is"
    (is (= inc (form->function inc)))
    (is (= :foo (form->function :foo)))
    (is (= 2 ((form->function #(inc %)) 1))))
  (testing "List with function and no arguments returns that function"
    (is (= inc (form->function (list inc))))
    (is (= :foo (form->function (list :foo)))))
  (testing "List with function and multiple arguments behaves as ->"
    (is (= [1 2] ((eval (form->function (list vector 2))) 1))))
  (testing "% can be used to change location of argument"
    (is (= [1 2] ((eval (form->function (list vector 1 '%))) 2)))))

(deftest test-tramp->
  (testing "tramp-> with no function"
    (is (= 1 (tramp-> 1))))
  (testing "tramp-> with non-form function"
    (is (= 2 (tramp-> 1 inc))))
  (testing "tramp-> with single form function"
    (is (= 2 (tramp-> 1 (inc))))
    (is (= 2 (tramp-> 1 (inc %))))
    (is (= [1 2] (tramp-> 1 (vector % 2))))
    (is (= [1 2] (tramp-> 2 (vector 1 %)))))
  (let [t (tramp-> 1 (inc) !(inc) (str))]
    (testing "Result of tramp-> with ! prefix is a jump function"
      (is (fn? t))
      (is (= inc (:fn (meta t))))
      (is (= [2] (:args (meta t))))
      (is (= "3" (t)))
      (is (= "10" (t 10)))))
  (let [f (fn [] (tramp-> 1 !(inc) !(inc) !(str)))]
    (testing "Each successive evaluation is trampolined"
      (is (= "3" ((((f))))))
      (is (= "3" (trampoline f)))))
  (testing "Handles extra args in jump function"
    (is (= "2foo" (trampoline (tramp-> 1 inc ! (str "foo")))))
    (is (= "foo2" (trampoline (tramp-> 1 inc ! (str "foo" %)))))))

(deftest test-if->
  (testing "one way"
    (let [f (fn [i] (tramp-> i
                             (if-> odd?
                               (inc))))]
      (is (= 2 (f 2)))
      (is (= 2 (f 1)))))
    
  (testing "two way"
    (let [f (fn [i] (tramp-> i
                             (if-> odd?
                               ((return :odd)) ; NB: only returns from inner scope
                               ((inc)
                                (inc)))
                             str))]
      (is (= ":odd" (f 1)))
      (is (= "4" (f 2))))))

(deftest test-return
  (is (= :return (tramp-> 1
                          inc
                          (return :return)
                          inc))))

(deftest test-guard
  (is (= 3 (tramp-> 1
                    (guard odd?) 
                    inc
                    inc)))
  (is (= nil (tramp-> 1
                    inc
                    (guard odd?)
                    inc))))

(deftest test-test-functions
  (let [t (tramp-> 1
                   ! inc
                   ! (+ 3)
                   inc)]
    (testing "Basic tests"
      (-> t
          (is-fn inc)
          (is-arg 1)
          (step!)
          (is-fn +)
          (is-args [2 3])
          (step!)
          (is-result 6)))
    (testing "Override step"
      (-> t
          (step! 10)
          (is-args [10 3])
          (step! 20)
          (is-result 21)))))
