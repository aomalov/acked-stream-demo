package Fibonacci

import java.math.BigInteger
import akka.actor._
import akka.stream._
import akka.stream.actor._
import akka.stream.scaladsl._
import akka.stream.stage.{PushStage, Stage}
import com.timcharper.acked._
import routing.AckedRouter

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise, Await}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Main extends App {
  val system = ActorSystem("example-stream-system")

  //Examples.startSimplePubSubExample(system)
  //Examples.startPubSubTransformerExample(system)
  //Examples.broadcastTest(system)
  Examples.builderFlowTest(system)
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
    for( a <- 1 to 10){
      publisherActor ! "test"
    }
    Thread.sleep(2000)

    implicit val timeout = Timeout(5 seconds)
    val f = ask(publisherActor,"testSpecial")
    //Got the future
    val res = Await.result(f,1 seconds)
    if(res.isInstanceOf[Future[Any]])
    {
//      println("%%%%%%%%%%% Got my future - waiting on it")
//      var resAck=Await.result(res.asInstanceOf[Future[Any]],5 seconds)
//      println("%%%%%%%%%%% Got my future result - "+resAck)
      res.asInstanceOf[Future[Any]].onComplete({
        case Success(res) => println("%%%%%%% completed the future")
        case Failure(t) => // do something
      })(system.dispatcher)
    }

    for( a <- 1 to 10){
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

    val g1=Source(publisher).to(Sink(doubleSubscriber))
    val g2=Source(doublePublisher).to(Sink(subscriber))

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

    for((p,v)<-input) {
      p.future.onComplete {
        case Success(value) => println("[success] "+v)
        case Failure(t)=> println("[failure - "+t.getMessage+"] "+v)
      }
    }

    val source = AckedSource(input)

    val Seq(s1, s2) = (1 to 2) map { n => AckedSink.fold[Int, Int](0)(_ + _) }


    val g = AckedFlowGraph.closed(s1, s2 )((m1, m2 ) => (m1, m2)) { implicit b =>
      (s1, s2) =>

        import AckedFlowGraph.Implicits._

        val router = b.add(AckedRouter[Int](2,num=>{
          var res=Vector.empty[Int]

          if(num%3==0) res:+=0
          if(num%3==0 || num%4==0) res:+=1

          res
        }))

        val flow1:AckedFlow[Int, Int, Unit] = AckedFlow(Flow[AckTup[Int]].map {
          case (p:Promise[Unit],v:Int) =>
            //Thread.sleep(50)
            println("[flow1] "+v+" // "+p.isCompleted)
            (p,v)
        })

        //Filter simulation - denies odd values
        val flow2:AckedFlow[Int, Int, Unit] = AckedFlow(Flow[AckTup[Int]].filter {
          case (p:Promise[Unit],v:Int) =>
            //Thread.sleep(50)
            println("[flow2] "+v+" // "+p.isCompleted)
            if(v%2!=0) p.failure(new Exception("odd value"))
            v%2==0
        })

        val flow2_1:AckedFlow[Int, Int, Unit] = AckedFlow(Flow[AckTup[Int]].map {
          case (p:Promise[Unit],v:Int) =>
            println("[flow2_1] "+v+" // "+p.isCompleted)
            (p,v)
        })

        source ~> router
        router.buffer(10, failOnOverflow = false) ~> flow1  ~> s1
        router.buffer(10, failOnOverflow = false) ~> flow2 ~> flow2_1 ~> s2
    }

    val (f1, f2) = g.run()
    val (r1, r2) = (Await.result(f1,100 seconds), Await.result(f2,100 seconds))
    println(r1)
    println(r2)
  }

  def simpleFlowTest(implicit system: ActorSystem):Unit={
    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val input = (Stream.continually(Promise[Unit]) zip Range.inclusive(1, 15)).toList

    for((p,v)<-input) {
      p.future.onComplete {
        case Success(value) => println("[success] "+v)
        case Failure(t)=> println("[failure - "+t.getMessage+"] "+v)
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
    println(Await.result(sum,10 seconds))
    Thread.sleep(500)
    system.shutdown()
  }

  def builderFlowTest(implicit system: ActorSystem): Unit ={

    implicit val ec = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val pg = Sink() { implicit builder =>

      //val broadcast = builder.add(Broadcast[Int](3))

      val f1 = Flow[Int].map(_ + 10)
      val f3 = Flow[Int].map(_.toString)
      val f2 = Flow[Int].map(_ + 20)
      val sink1: Sink[Int,Future[Int]] = Sink.fold[Int,Int](0)(_+_)

      val fl1=builder.add(f1)
      builder.addEdge(fl1.outlet,builder.add(sink1))
      //builder.addEdge(broadcast.out(0), f1, builder.add(sink1))
//      builder.addEdge(broadcast.out(1), f2, builder.add(Sink.foreach(println)))
//      builder.addEdge(broadcast.out(2), f3, builder.add(Sink.foreach(println)))

      //broadcast.in
      fl1.inlet
    }

//      : Future[(Int,Unit,Unit)]
    val x = Source(1 to 3).toMat(pg)(Keep.right).run()

//    val g = FlowGraph.closed() { builder: FlowGraph.Builder[Unit] =>
//      val in = Source(1 to 3)
//
//      val broadcast = builder.add(Broadcast[Int](3))
//
//      val f1 = Flow[Int].map(_ + 10)
//      val f3 = Flow[Int].map(_.toString)
//      val f2 = Flow[Int].map(_ + 20)
//      val sink1 = Sink.fold[Int,Int](0)(_+_)
//
//      builder.addEdge(builder.add(in), broadcast.in)
//      builder.addEdge(broadcast.out(0), f1, builder.add(sink1))
//      builder.addEdge(broadcast.out(1), f2, builder.add(Sink.foreach(println)))
//      builder.addEdge(broadcast.out(2), f3, builder.add(Sink.foreach(println)))
//    }.run()

    println(x)


    val pairUpWithToString = Sink() { implicit b =>
      import FlowGraph.Implicits._

      // prepare graph elements
      val broadcast = b.add(Broadcast[Int](2))
      val zip = b.add(Zip[Int, String]())
      val sink: Sink[(Int,String),(Int,String)]= Flow[(Int,String)].toMat(Sink.foreach(println))

      // connect the graph
      broadcast.out(0).map(identity) ~> zip.in0
      broadcast.out(1).map(_+1).map(_.toString) ~> zip.in1
      zip.out ~> sink

      // expose ports
      broadcast.in
    }

     val (x1,x2) = pairUpWithToString.runWith(Source(List(1)), Sink.head)

    println(Await.result(x2,10 seconds))
  }
}