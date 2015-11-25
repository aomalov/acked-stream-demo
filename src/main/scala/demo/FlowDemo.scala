package demo

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.timcharper.acked.{AckedSource,AckedSink,AckedFlow}
import scala.concurrent.{Promise,ExecutionContext,Future}

object FlowDemo extends App {
  implicit val actorSystem = ActorSystem("SourceDemo")
  implicit val materializer = ActorMaterializer()
  import actorSystem.dispatcher

  val input = (1 to 50) map { n =>
    val p = Promise[Unit]
    p.future.onComplete { v =>
      println(s"                        ${n} - acked (${v})")
    }
    (p, n)
  }


  val outputFlow = AckedFlow[Int].map { n =>
    Thread.sleep(100)
    println(s"Processing ${n}")
    n
  }

  def sinkInstance(which: String) =
    AckedSink.foreach[Int] { n =>
      println(s"                ${n} reached ${which} sink")
    }

  // If the message flows to any acked sink, it is acknowledged.
  AckedSource(input).
    groupBy(_ % 2 == 0).
    mapAsync(2) {
      //group key , group values stream
      case (false, source) =>
        source.
          via(outputFlow).
          runWith(sinkInstance("odd"))
      case (true, source) =>
        source.
          via(outputFlow).
          runWith(sinkInstance("even"))
    }.
    runForeach { _ => println(s"Done with group ") }.
    onComplete { _ =>
      actorSystem.shutdown()
    }
}
