package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, SinkShape, SourceShape}

object OpenGraphs extends App {

  implicit val system = ActorSystem("OpenGraphs")
  implicit val mt = ActorMaterializer()(system)

  /**
    * A compose source that concatenates 2 sources
    * - emits ALL the elements from the first source
    * - then ALL the elements from the second
    */

  val source1 = Source(1 to 10)
  val source2 = Source(42 to 1000)

  // Step 1
  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._ // bring us ~>

      // Step 2
      val concat = builder.add(Concat[Int](2))

      // Step 3
      source1 ~> concat
      source2 ~> concat

      // Step 4
      SourceShape(concat.out)
    }
  )

  // sourceGraph.to(Sink.foreach[Int](println)).run()(mt)
//  sourceGraph.runWith(Sink.foreach(println))

  /**
    * one Source ~> two Sinks
    */
  val sink1 = Sink.foreach[Int](x => println(s"Sink1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Sink2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

//  source1.to(sinkGraph).run()

  /**
    * EX1 :  complex flow
    * - one that adds 1 to a number
    * - one that does number * 10
    */
  val incrementFlow = Flow[Int].map(_ + 1)
  val upTo10Flow = Flow[Int].map(_ * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val incrementerShape = builder.add(incrementFlow)
      val multiplyerShape = builder.add(upTo10Flow)

      incrementerShape ~> multiplyerShape

      FlowShape[Int, Int](incrementerShape.in, multiplyerShape.out)
    }
  )
//  source1.via(flowGraph).to(Sink.foreach(println)).run() // Eq to:
//  flowGraph.runWith(source1, Sink.foreach(println))

  /**
    * EX: flow from a sink to a flow
    */
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>

        // declare SHAPES
        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        // tying - nothing

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )

  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
