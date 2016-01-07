/**
  * Created by andrewm on 12/15/2015.
  */

import org.scalatest.{ParallelTestExecution, BeforeAndAfterAll, FunSuite}

class test_p extends FunSuite with BeforeAndAfterAll with ParallelTestExecution {

  override def beforeAll() = println("before")
  override def afterAll()  = println("after")

  println("constructor")
  TestTimer(2)
  println("singleton "+TestTimer.currentCount())

  test("test 1") {
    println("1a")
    Thread.sleep(3000)
    println("1b")
  }

  test("test 2") {
    for(i<- 1 to 10) {
      println("2_" + i)
      Thread.sleep(500)
    }
  }

}

object TestTimer {
  private var count:Long = 0

  def currentCount(): Long = {
    count += 1
    count
  }

  def apply(init:Long)={
    if(count==0) count=init
  }
}