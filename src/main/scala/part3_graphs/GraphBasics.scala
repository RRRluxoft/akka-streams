package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}
import akka.stream.{ActorMaterializer, ClosedShape}

object GraphBasics extends App {

  implicit val system = ActorSystem("GraphBasics")
  implicit val mt = ActorMaterializer()(system)

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier = Flow[Int].map(_ * 10)
  val output = Sink.foreach[(Int, Int)](println)

  // *Step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._

      // *Step 2 - add the necessary components for the graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int, Int])                      // fan-in operator

      // *Step 3 - tying up the components
      input ~> broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      // *Step 4 - return a closed shape
      ClosedShape // FREEZE the builder's shape
      // shape
    } // graph
  )   // runnable graph

//  graph.run()(mt) // run graph and materialize it

  /**
    * Ex1: feed ~> a source into 2 sinks at the same time
    */
  val graph2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))

      val sink1 = Sink.foreach[Int](x => println(s"First sink: $x"))
      val sink2 = Sink.foreach[Int](x => println(s"Second sink: $x"))

//      input ~> broadcast
//      broadcast.out(0) ~> incrementer ~> sink1
//      broadcast.out(1) ~> multiplier ~> sink2
//      equivalent to:
      input ~> broadcast ~> incrementer ~> sink1  // implicit port numbering
               broadcast ~> multiplier ~> sink2

      ClosedShape
    }
  )

//  graph2.run()

  /**
    * Ex2 - fast and slow sources ~> merge ~> balance ~> two sinks
    */
  import scala.concurrent.duration._
  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 seconds)

  val sink1 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 1 number of elements: $count")
    count + 1
  })
  val sink2 = Sink.fold[Int, Int](0)((count, _) => {
    println(s"Sink 2 number of elements: $count")
    count + 1
  })

  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._


      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      fastSource ~> merge ~> balance ~> sink1
      slowSource ~> merge
      balance ~> sink2

      ClosedShape
    }
  )

  balanceGraph.run()
}
