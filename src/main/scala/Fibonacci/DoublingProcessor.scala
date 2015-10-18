package Fibonacci

import java.math.BigInteger
import scala.collection.mutable.{Queue => MQueue}
import akka.actor._
import akka.stream.actor._

import ActorPublisherMessage._
import ActorSubscriberMessage._

class DoublingProcessor extends ActorSubscriber with ActorPublisher[BigInteger] with ActorLogging {
  val dos = BigInteger.valueOf(1L)  //no actual multiplication
  val doubledQueue = MQueue[BigInteger]()

  def receive = {
    case OnNext(biggie: BigInteger) =>
      log.debug("[FibonacciDoubler>>>] Received Fibonacci Number: {} from {}", biggie, sender())
      Thread.sleep(500)
      doubledQueue.enqueue(biggie.multiply(dos))
      sendDoubled("OnNext")
    case OnError(err: Exception) => 
      onError(err)
      context.stop(self)
    case OnComplete => 
      onComplete()
      context.stop(self)
    case Request(cnt) =>
      log.debug("[FibonacciDoubler<<<] Received Request ({}) from {}", cnt,sender())
      sendDoubled("Request")  //at first we have nothing to send - and pushing in from OnNext handler
    case Cancel =>
      cancel()
      context.stop(self)
    case _ =>
  }

  def sendDoubled(from:String) {
    while(isActive && totalDemand > 0 && !doubledQueue.isEmpty) {
      onNext(doubledQueue.dequeue())
    }
    log.debug("<><>doubler sending from event {}",from)
  }

  val requestStrategy = new MaxInFlightRequestStrategy(50) {
    def inFlightInternally(): Int = { doubledQueue.size }
  }
}