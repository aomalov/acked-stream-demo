package Fibonacci

import java.math.BigInteger

import akka.actor._
import akka.stream.actor.ActorPublisherMessage._
import akka.stream.actor._

import scala.concurrent.{ExecutionContext, Promise}

class FibonacciSimplePublisher extends ActorPublisher[BigInteger] with ActorLogging {
  var prev = BigInteger.ZERO
  var curr = BigInteger.ZERO
  var buf = Vector.empty[String]
  var bufPromise = Vector.empty[Promise[Any]]

  def receive = {
    case "test" =>
      processStringReactiveMsg("test")
    case "testSpecial" =>
      processStringReactiveMsg("SPECIAL")
    case "stop" =>
      onCompleteThenStop()
    case Request(cnt) => 
      log.debug("[FibonacciPublisher] Received Request ({}) from {}", cnt,sender())
      //sendNonFibs()
      sendFibs()
    case Cancel => 
      log.info("[FibonacciPublisher] Cancel Message Received -- Stopping")
      context.stop(self)
    case _ =>
  }

  def processStringReactiveMsg(msg:String) ={
    log.debug(">>>>>>>>>>>>>>>>>>[FibonacciPublisher] Received async msg "+msg+" with demand of: " + totalDemand)
    buf:+=msg+" from outer"
    val p=createAckRoutine()
    bufPromise:+=p
  }

  def createAckRoutine():Promise[Any] = {
    implicit val executionContext = context.system.dispatcher

    val p = Promise[Any]
    p.future.onComplete({ v =>  println(s"OUTER___ACKED [[${v}]]") })
    p
  }

  def sendFibs() {
    implicit val executionContext = context.system.dispatcher
    while(isActive && totalDemand > 0) {
      Thread.sleep(1000)
      onNext(nextFib(executionContext))
    }
  }


  def nextFib(implicit executionContext: ExecutionContext): BigInteger = {

    if(curr == BigInteger.ZERO) {
      curr = BigInteger.ONE
    } else {
      val tmp = prev.add(curr)
      prev = curr
      curr = tmp
    }
    //Finishing stream if more than 10000
    //if(curr.compareTo(BigInteger.valueOf(10000))<0 ) onCompleteThenStop()
    curr
  }

  def nextNonFib(implicit executionContext: ExecutionContext,pr:Promise[Any],vStr: String): (Promise[Any],Any) = {
    (pr,vStr)
  }
}