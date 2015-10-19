package Fibonacci

import java.math.BigInteger
import akka.actor._
import akka.stream.actor._

import ActorPublisherMessage._

import scala.concurrent.{Future, ExecutionContext, Promise}

class FibonacciPublisher extends ActorPublisher[(Promise[Any],Any)] with ActorLogging {
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
      sendNonFibs()
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
    sender() ! p.future
    sendNonFibs()
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
      onNext(nextFib(executionContext))
    }
  }

  def sendNonFibs() {
    implicit val executionContext = context.system.dispatcher

    log.debug("****buf size before {}", buf.size)
    while(buf.size>0 &&  isActive && totalDemand > 0) {
      onNext(nextNonFib(executionContext,bufPromise(0), buf(0)))
      val (use,keep)=buf.splitAt(1)
      val (useP,keepP)=bufPromise.splitAt(1)
      log.debug(use+ "  "+ keep)
      buf=keep
      bufPromise=keepP
      log.debug("****buf size after {}", buf.size)
    }
  }

  def nextFib(implicit executionContext: ExecutionContext): (Promise[Any],Any) = {

    if(curr == BigInteger.ZERO) {
      curr = BigInteger.ONE
    } else {
      val tmp = prev.add(curr)
      prev = curr
      curr = tmp
    }
    //Finishing stream if more than 10000
    //if(curr.compareTo(BigInteger.valueOf(10000))<0 ) onCompleteThenStop()
    val p = Promise[Any]
    p.future.onComplete({ v =>  println(s"A_C_K_E_D (${v})") })
    (p,curr)
  }

  def nextNonFib(implicit executionContext: ExecutionContext,pr:Promise[Any],vStr: String): (Promise[Any],Any) = {
    (pr,vStr)
  }
}