package demo

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.timcharper.acked.{AckedBroadcast, AckedSource}
import scala.concurrent.{Promise,ExecutionContext,Future}

object SourceDemo extends App {
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

  val source = AckedSource(input)

  AckedBroadcast

  val done = source.
    map { n =>
      Thread.sleep(100)
      println(s"${n} - map")
      n
    }.
    filter { n =>
      if (n % 3 == 0) {
        true
      } else {
        println(s"        ${n} - filtered")
        false
      }
    }.
    grouped(5).
    mapConcat(identity).
    runForeach { n =>
      Thread.sleep(100)
      println(s"                ${n} - sink")
    }
  done.onComplete { _ =>
    actorSystem.shutdown()
  }
}
