package Fibonacci

import java.math.BigInteger
import akka.actor._
import akka.stream.actor._

import scala.concurrent.{Future, Promise, Await}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

object Main extends App {
  val system = ActorSystem("example-stream-system")

  Examples.startSimplePubSubExample(system)
  //Examples.startPubSubTransformerExample(system)
  //Thread.sleep(10000)
  //system.shutdown()
}

object Examples {

  def startSimplePubSubExample(system: ActorSystem) {
    system.log.info("Starting Publisher")
    val publisherActor = system.actorOf(Props[FibonacciPublisher])
    val publisher = ActorPublisher[(Promise[Unit],BigInteger)](publisherActor)

    system.log.info("Starting Subscriber")
    val subscriberActor = system.actorOf(Props(new FibonacciSubscriber(1)))
    val subscriber = ActorSubscriber[(Promise[Unit],BigInteger)](subscriberActor)

    system.log.info("Subscribing to Publisher")
    publisher.subscribe(subscriber)
    Thread.sleep(20)
    publisherActor ! "test"
    Thread.sleep(2000)
    var a = 0;
    // for loop execution with a range
    for( a <- 1 to 50){
      publisherActor ! "test"
    }
    Thread.sleep(2000)

    implicit val timeout = Timeout(5 seconds)
    val f = ask(publisherActor,"testSpecial")
    //Got the future
    val res = Await.result(f,1 seconds)
    if(res.isInstanceOf[Future[Any]])
    {
      println("%%%%%%%%%%% Got my future - waiting on it")
      var resAck=Await.result(res.asInstanceOf[Future[Any]],5 seconds)
      println("%%%%%%%%%%% Got my future result - "+resAck)
    }

    for( a <- 1 to 50){
      publisherActor ! "test"
    }
    Thread.sleep(8000)
    publisherActor ! "stop"
    Thread.sleep(4000)
    system.shutdown()
  }

  def startPubSubTransformerExample(system: ActorSystem) {
    system.log.info("Starting Publisher")
    val publisherActor = system.actorOf(Props[FibonacciPublisher])
    val publisher = ActorPublisher[BigInteger](publisherActor)

    system.log.info("Starting Doubling Processor")
    val doubleProcessorActor = system.actorOf(Props[DoublingProcessor])
    val doublePublisher = ActorPublisher[BigInteger](doubleProcessorActor)
    val doubleSubscriber = ActorSubscriber[BigInteger](doubleProcessorActor)

    system.log.info("Starting Subscriber")
    val subscriberActor = system.actorOf(Props(new FibonacciSubscriber(500)))
    val subscriber = ActorSubscriber[BigInteger](subscriberActor)

    system.log.info("Subscribing to Processor to Publisher")
    publisher.subscribe(doubleSubscriber)
    system.log.info("Subscribing Subscriber to Processor")
    doublePublisher.subscribe(subscriber)
  }
}