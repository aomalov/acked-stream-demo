package routing

import akka.stream._
import akka.stream.scaladsl._
import com.timcharper.acked._

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * Created by andrewm on 11/23/2015.
  */

private object SameThreadExecutionContext extends ExecutionContext {
  def execute(r: Runnable): Unit =
    r.run()
  override def reportFailure(t: Throwable): Unit =
    throw new IllegalStateException("problem in op_rabbit internal callback", t)
}

class AckedRouter[T](g: Graph[UniformFanOutShape[AckTup[T], AckTup[T]], Unit]) extends AckedGraph[AckedUniformFanOutShape[T, T], Unit] {
  val shape = new AckedUniformFanOutShape(g.shape)
  val akkaGraph = g
}

object AckedRouter {
  private class AckedRouterImpl[T](outputPorts: Int, routerFunction: T=>Vector[Int])
    extends FlexiRoute[AckTup[T], UniformFanOutShape[AckTup[T], AckTup[T]]](new UniformFanOutShape(outputPorts), Attributes.name("AckedRouter"))
  {
    import FlexiRoute._

    implicit val ec = SameThreadExecutionContext

    override def createRouteLogic(p: PortT) = new RouteLogic[AckTup[T]] {
      override def initialState =
        State[Any](DemandFromAll(p.outlets)) {
          //second param in CASE - should be outport - it is used when we apply DemandFromAny -
          // shows the port which is ready for emitting an element downstream
          // for DemandFromAll - just a placeholder
          case (ctx, _, (ackPromise, element)) =>
            val destPortsNumbers = routerFunction(element)
            val destPorts=for(num <- destPortsNumbers) yield p.outArray(num)
            val downstreamAckPromises = List.fill(destPorts.size)(Promise[Unit])
            (downstreamAckPromises,destPorts,destPortsNumbers).zipped.toList.foreach {
              case (downstreamAckPromise, port, portNum) =>
                println( element + " emitted to port #" + (portNum+1))
                ctx.emit(port.asInstanceOf[Outlet[AckTup[T]]])((downstreamAckPromise, element))
            }
            if(destPorts.isEmpty)
              ackPromise.success(null) //FILTERED out on router
            else
              ackPromise.completeWith(Future.sequence(downstreamAckPromises.map(_.future)).map(_ => ()))

            SameState
        }


      override def initialCompletionHandling = eagerClose
    }
  }


  def apply[T](outputPorts: Int, routerFunction: T=>Vector[Int]) = {
    val thing = new AckedRouterImpl[T](outputPorts,routerFunction)
    new AckedRouter[T](thing)
  }
}
