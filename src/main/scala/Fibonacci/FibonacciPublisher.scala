package Fibonacci

import java.math.BigInteger
import akka.actor._
import akka.stream.actor._

import ActorPublisherMessage._

import scala.concurrent.{ExecutionContext, Promise}

class FibonacciPublisher extends ActorPublisher[(Promise[BigInteger],BigInteger)] with ActorLogging {
  var prev = BigInteger.ZERO
  var curr = BigInteger.ZERO

  def receive = {
    case Request(cnt) => 
      log.debug("[FibonacciPublisher] Received Request ({}) from {}", cnt,sender())
      sendFibs()
    case Cancel => 
      log.info("[FibonacciPublisher] Cancel Message Received -- Stopping")
      context.stop(self)
    case _ =>
  }

  def sendFibs() {
    implicit val executionContext = context.system.dispatcher
    while(isActive && totalDemand > 0) {
      onNext(nextFib(executionContext))
    }
  }

  def nextFib(implicit executionContext: ExecutionContext): (Promise[BigInteger],BigInteger) = {

    if(curr == BigInteger.ZERO) {
      curr = BigInteger.ONE
    } else {
      val tmp = prev.add(curr)
      prev = curr
      curr = tmp
    }
    //Finishing stream if more than 10000
    //if(curr.compareTo(BigInteger.valueOf(10000))<0 ) onCompleteThenStop()
    val p = Promise[BigInteger]
    p.future.onComplete({ v =>  println(s"A_C_K_E_D (${v})") })
    (p,curr)
  }
}