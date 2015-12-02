package Fibonacci

import java.math.BigInteger
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiFunction
import akka.actor._
import akka.stream._
import akka.stream.actor._
import akka.stream.scaladsl._
import com.timcharper.acked._
import routing.AckedRouter

import scala.collection.mutable
import scala.compat.java8.FutureConverters
import scala.concurrent.{ExecutionContext, Future, Promise, Await}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scala.language.dynamics

object Main extends App {
  val system = ActorSystem("example-stream-system")

  //Examples.startSimplePubSubExample(system)
  //Examples.startPubSubTransformerExample(system)
  //Examples.broadcastTest(system)
  Examples.builderFlowTest(system)
  //Examples.fullyDynamic()
  //Examples.varargToList
  //Examples.evalTest()
  //Thread.sleep(10000)
  //system.shutdown()
}

object Examples {

  def startSimplePubSubExample(system: ActorSystem) {
    system.log.info("Starting Publisher")
    val publisherActor = system.actorOf(Props[FibonacciPublisher])
    val publisher = ActorPublisher[(Promise[Unit], BigInteger)](publisherActor)

    system.log.info("Starting Subscriber")
    val subscriberActor = system.actorOf(Props(new FibonacciSubscriber(1)))
    val subscriber = ActorSubscriber[(Promise[Unit], BigInteger)](subscriberActor)

    system.log.info("Subscribing to Publisher")
    publisher.subscribe(subscriber)
    Thread.sleep(20)
    publisherActor ! "test"
    Thread.sleep(2000)
    var a = 0;
    // for loop execution with a range
    for (a <- 1 to 10) {
      publisherActor ! "test"
    }
    Thread.sleep(2000)

    implicit val timeout = Timeout(5 seconds)
    val f = ask(publisherActor, "testSpecial")
    //Got the future
    val res = Await.result(f, 1 seconds)
    if (res.isInstanceOf[Future[Any]]) {
      //      println("%%%%%%%%%%% Got my future - waiting on it")
      //      var resAck=Await.result(res.asInstanceOf[Future[Any]],5 seconds)
      //      println("%%%%%%%%%%% Got my future result - "+resAck)
      res.asInstanceOf[Future[Any]].onComplete({
        case Success(res) => println("%%%%%%% completed the future")
        case Failure(t) => // do something
      })(system.dispatcher)
    }

    for (a <- 1 to 10) {
      publisherActor ! "test"
    }
    Thread.sleep(8000)
    publisherActor ! "stop"
    Thread.sleep(4000)
    system.shutdown()

  }

  def startPubSubTransformerExample(system: ActorSystem) {
    implicit val materializer = ActorMaterializer()(system)

    system.log.info("Starting Publisher")
    val publisherActor = system.actorOf(Props[FibonacciSimplePublisher])
    val publisher = ActorPublisher[BigInteger](publisherActor)

    system.log.info("Starting Doubling Processor")
    val doubleProcessorActor = system.actorOf(Props[DoublingProcessor])
    val doublePublisher = ActorPublisher[BigInteger](doubleProcessorActor)
    val doubleSubscriber = ActorSubscriber[BigInteger](doubleProcessorActor)

    system.log.info("Starting Subscriber")
    val subscriberActor = system.actorOf(Props(new FibonacciSubscriber(500)))
    val subscriber = ActorSubscriber[BigInteger](subscriberActor)

    val g1 = Source(publisher).to(Sink(doubleSubscriber))
    val g2 = Source(doublePublisher).to(Sink(subscriber))

    //    doublePublisher.subscribe(doubleSubscriber.)
    //Flow[BigInteger].transform(()=>new Stage[] {})

    g1.run()
    Thread.sleep(2000)
    g2.run()



    //    system.log.info("Subscribing to Processor to Publisher")
    //    publisher.subscribe(doubleSubscriber)
    //    system.log.info("Subscribing Subscriber to Processor")
    //    doublePublisher.subscribe(subscriber)
  }

  def broadcastTest(implicit system: ActorSystem): Unit = {

    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val input = (Stream.continually(Promise[Unit]) zip Range.inclusive(1, 15)).toList

    for ((p, v) <- input) {
      p.future.onComplete {
        case Success(value) => println("[success] " + v)
        case Failure(t) => println("[failure - " + t.getMessage + "] " + v)
      }
    }

    val source = AckedSource(input)

    val Seq(s1, s2) = (1 to 2) map { n => AckedSink.fold[Int, Int](0)(_ + _) }


    val g = AckedFlowGraph.closed(s1, s2)((m1, m2) => (m1, m2)) { implicit b =>
      (s1, s2) =>

        import AckedFlowGraph.Implicits._

        val router = b.add(AckedRouter[Int](2, num => {
          var res = Vector.empty[Int]

          if (num % 3 == 0) res :+= 0
          if (num % 3 == 0 || num % 4 == 0) res :+= 1

          res
        }))

        val flow1: AckedFlow[Int, Int, Unit] = AckedFlow(Flow[AckTup[Int]].map {
          case (p: Promise[Unit], v: Int) =>
            //Thread.sleep(50)
            println("[flow1] " + v + " // " + p.isCompleted)
            (p, v)
        })

        //Filter simulation - denies odd values
        val flow2: AckedFlow[Int, Int, Unit] = AckedFlow(Flow[AckTup[Int]].filter {
          case (p: Promise[Unit], v: Int) =>
            //Thread.sleep(50)
            println("[flow2] " + v + " // " + p.isCompleted)
            if (v % 2 != 0) p.failure(new Exception("odd value"))
            v % 2 == 0
        })

        val flow2_1: AckedFlow[Int, Int, Unit] = AckedFlow(Flow[AckTup[Int]].map {
          case (p: Promise[Unit], v: Int) =>
            println("[flow2_1] " + v + " // " + p.isCompleted)
            (p, v)
        })

        source ~> router
        Seq(s1, s2).zip(Seq(flow1, flow2)).foreach {
          case (sN, fN) => router ~> fN ~> sN
        }

      //        router ~> flow1  ~> s1
      //        router.buffer(10, failOnOverflow = false) ~> flow2 ~> flow2_1 ~> s2
      //b.addEdge(router.akkaShape.out(0),flow1,b.add(s1))
    }

    val (f1, f2) = g.run()
    val (r1, r2) = (Await.result(f1, 100 seconds), Await.result(f2, 100 seconds))
    println(r1)
    println(r2)

    Thread.sleep(500)
    system.shutdown()
  }

  def simpleFlowTest(implicit system: ActorSystem): Unit = {
    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val input = (Stream.continually(Promise[Unit]) zip Range.inclusive(1, 15)).toList

    for ((p, v) <- input) {
      p.future.onComplete {
        case Success(value) => println("[success] " + v)
        case Failure(t) => println("[failure - " + t.getMessage + "] " + v)
      }
    }

    val source = AckedSource(input)

    val sink1 = AckedSink.fold[Int, Int](0)(_ + _)
    val sink2 = AckedSink.fold[Int, Int](0)(_ + _)

    //val router = AckedRouter[Int](2)

    // connect the Source to the Sink, obtaining a RunnableGraph
    val runnable: RunnableGraph[Future[Int]] = source.toMat(sink1)(Keep.right)

    // materialize the flow and get the value of the FoldSink
    val sum: Future[Int] = runnable.run()
    println(Await.result(sum, 10 seconds))
    Thread.sleep(500)
    system.shutdown()
  }

  def builderFlowTest(implicit system: ActorSystem): Unit = {

    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()


    val outerFuture =
      Source(1 to 118).toMat(outputSink(system))(Keep.right).run()

    FutureConverters.toScala(outerFuture).onComplete {
      case Success(_) => println("succesful sink")
        system.shutdown()
      case Failure(t) =>println("failed sink")
    }


//    val resultFuture=outerFuture match {
//      case fut:Future[Future[Unit]] =>
//        fut.onComplete {
//                case Success(nestedFuture) => println("successful outer future")
//                  nestedFuture.onComplete {
//                    case Success(_) => println("succesful sink")
//                      system.shutdown()
//                    case Failure(t) =>println("failed sink")
//                  }
//                case Failure(t) => println("failed outer future")
//              }
//      case _  => new Exception("bad flow materialization")
//    }

  }

  def builderTest2(implicit system:ActorSystem) = {

    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val y = Source(1 to 3).map(_ * 10).runFold(0)(_ + _)

    println(Await.result(y, 10 seconds))

    val pairUpWithToString = Flow() { implicit b =>
      import FlowGraph.Implicits._

      // prepare graph elements
      val broadcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, String]())
      //val sink= Flow[(Int,String)].toMat(Sink.foreach(println))

      // connect the graph
      broadcast.out(0).map(_ * 10).map(identity) ~> zip.in0
      broadcast.out(1).map(_ + 1).map(_.toString) ~> zip.in1

      // expose ports
      (broadcast.in, zip.out)
    }

    val (t1, t2) = pairUpWithToString.runWith(Source(List(1)), Sink.head)

    val pickMaxOfThree = FlowGraph.partial() { implicit b =>
      import FlowGraph.Implicits._

      val zip1 = b.add(ZipWith[Int, Int, Int](math.max ))
      val zip2 = b.add(ZipWith[Int, Int, Int](math.max))
      zip1.out ~> zip2.in0

      UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
    }

    val resultSink = Sink.head[Int]

    val g = FlowGraph.closed(resultSink) { implicit b =>
      sink =>
        import FlowGraph.Implicits._

        // importing the partial graph will return its shape (inlets & outlets)
        val pm3 = b.add(pickMaxOfThree)

        Source.single(1) ~> pm3.in(0)
        Source.single(2) ~> pm3.in(1)
        Source.single(3) ~> pm3.in(2)
        pm3.out ~> sink.inlet
    }

    val max: Future[Int] = g.run()
    println(Await.result(max, 300.millis))
  }

  def outputSink(implicit system: ActorSystem): Sink[Int, CompletionStage[Void]] = {
    import akka.stream.scaladsl.FlowGraph.Implicits._
    implicit val ec = system.dispatcher



    val totalSink3: Sink[Future[Unit],CompletionStage[Void]] = Flow[Future[Unit]].toMat(Sink.head)(combinerMaterializer)

    Sink(totalSink3) {
      implicit builder => out1 => {
        val bcast = builder.add(Broadcast[Int](3))

        val p1=Promise[Unit]()
        val outSink1=Sink.fold[Int, Int](0)(_+_).mapMaterializedValue(fut=>{
          fut.onComplete(v=>println("Sink 1 is finished with ["+v+"]"))
          p1.completeWith(fut.map(_=>()))
          fut
        })

        val p2=Promise[Unit]()
        val outSink2 = Sink.fold[Int, Int](0)(_+2*_).mapMaterializedValue(fut=>{
          fut.onComplete(v=>println("Sink 2 is finished with ["+v+"]"))
          p2.completeWith(fut.map(_=>()))
          fut
        })

        val p3=Promise[Unit]()
        val outSink3 = Sink
//          .fold[Int, Int](0)(_+3*_)
          .foreach[Int](v=>{
            println("[got value] - "+v)
            if(v==101) throw new Exception("just a flow exception")
        })
          .mapMaterializedValue(fut=>{
          fut.onComplete(v=>println("Sink 3 is finished with ["+v+"]"))
          p3.completeWith(fut.map(_=>()))
          fut
        })


        bcast ~> outSink1
        bcast ~> outSink2
        bcast ~> outSink3

        builder.materializedValue.map(_ => {
          //can also use Future.sequence()
          val totalFuture=whenAllComplete(p1.future,p2.future,p3.future)
          //println(totalFuture)
          totalFuture
        }).outlet ~> out1

        bcast.in
      }
    }
  }

  def fullyDynamic()= {
    // applyDynamic example
    object OhMy extends Dynamic {
      def applyDynamic(methodName: String)(args: Any*) {
        println(s"""|  methodName: $methodName,
                    |args: ${args.mkString(",")}""".stripMargin)
      }
    }

    OhMy.dynamicMethod("with", "some", 1337)


    object JSON_Dynamic extends Dynamic {
      def applyDynamicNamed(methodName: String)(args: (String, Any)*) {
        for(arg <-args)
          println(s"""Calling a method ${methodName.toUpperCase()}, for:\n "${arg._1}": "${arg._2}" """)
      }
    }

    JSON_Dynamic.node(nickname = "ktoso",nickname1 = "ktoso1")

    object MagicBox extends Dynamic {
      private var box = mutable.Map[String, Any]()

      def updateDynamic(propertyName: String)(value: Any) {
        value match {
          case stringValue:String => box(propertyName) = stringValue
          case intValue:Int       => box(propertyName) = intValue*2
        }
      }
      def selectDynamic(propertyName: String) = box(propertyName)
    }

    MagicBox.prop1="123"
    println(MagicBox.prop1)
    MagicBox.prop2=11
    println(MagicBox.prop2)
  }

  def varargToList() = {
    def foo(os: String*) =
      for(elem<-os) println(elem)
      //println(os.toList)

    val args = Seq("hi", "there")
    foo(args:_*)
  }

  def whenAllComplete[T](fs: Future[T]*)(implicit ec: ExecutionContext): Future[Unit] = {
    val remaining = new AtomicInteger(fs.length)
    val p = Promise[Unit]()

    fs foreach {
      _ onComplete {
        case Success(_) =>
          if (remaining.decrementAndGet() == 0) {
//            println("it was a success //" + Thread.currentThread().getName)
            p success()
          }
        case Failure(t) =>
//          println("it was a failure //"+ Thread.currentThread().getName)
          if(!p.isCompleted) {
            p.failure(t)
          }
      }
    }

    p.future
  }

  def combinerMaterializer(ignored: Any, nestedFutureRight: Future[Future[Unit]])(implicit ec: ExecutionContext) = {
    val p=Promise[Unit]
    nestedFutureRight.onComplete {
      case Success(innerFuture) => p.completeWith(innerFuture)
      case Failure(t) => throw new Exception(t)
    }
    FutureConverters.toJava(p.future.map(_ => null).mapTo[Void])
  }

}