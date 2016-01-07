package stopping

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Attributes, FanInShape}
import akka.stream.FanInShape.{Init, Name}
import akka.stream.scaladsl._

import scala.concurrent.{ Promise}
import scala.util.Random
import scala.concurrent.duration._

/**
  * Created by andrewm on 1/7/2016.
  */
object StopperFlow {

  private class StopperMergeShape[A](_init: Init[A] = Name("StopperFlow")) extends FanInShape[A](_init) {
    val in = newInlet[A]("in")
    val stopper = newInlet[Unit]("stopper")

    override protected def construct(init: Init[A]): FanInShape[A] = new StopperMergeShape[A](init)
  }

  private class StopperMerge[In] extends FlexiMerge[In, StopperMergeShape[In]](new StopperMergeShape(), Attributes.name("StopperMerge")) {
    import FlexiMerge._

    override def createMergeLogic(p: PortT) = new MergeLogic[In] {
      override def initialState =
        State[In](Read(p.in)) { (ctx, input, element) =>
          ctx.emit(element)
          SameState
        }

      override def initialCompletionHandling = eagerClose
    }
  }

  def apply[In](): Flow[In, In, Promise[Unit]] = {
    val stopperSource = Source.lazyEmpty[Unit]

    Flow(stopperSource) { implicit builder =>
      stopper =>
        val stopperMerge = builder.add(new StopperMerge[In]())

        builder.addEdge(stopper.outlet,stopperMerge.stopper)
        //stopper.outlet ~> stopperMerge.stopper

        (stopperMerge.in, stopperMerge.out)
    }
  }
}


object TestStopping extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val startTime = System.currentTimeMillis()

  def dumpToConsole(f: Float) = {
    val timeSinceStart = System.currentTimeMillis() - startTime
    System.out.println(s"[$timeSinceStart] - Random number: $f")
  }

  val randomSource = Source(() => Iterator.continually(Random.nextFloat()))
  val consoleSink = Sink.foreach(dumpToConsole)
  val flow = randomSource.viaMat(StopperFlow())(Keep.both).to(consoleSink)

  val (_, promise) = flow.run()

  Thread.sleep(50)
  promise.future.onComplete(_=> {
    system.shutdown()
  })
  Thread.sleep(50)
  promise.success(())
}