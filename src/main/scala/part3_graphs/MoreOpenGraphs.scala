package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, UniformFanInShape}

import java.util.Date

object MoreOpenGraphs extends App {

  implicit val system = ActorSystem("MoreOpenGraphs")
  implicit val mt = ActorMaterializer()(system)

  /**
    * Exmpl: Max3 operator
    * - 3 inputs of type int
    * - the maximum of the 3
    */

  // Step 1
  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // Step 2
    val max1 = builder.add(ZipWith[Int, Int, Int](Math.max))
    val max2 = builder.add(ZipWith[Int, Int, Int](Math.max))

    // Step 3
    max1.out ~> max2.in0

    // Step 4
    UniformFanInShape(outlet = max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._ // bring us ~>

      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)
      max3Shape.out ~> maxSink

      ClosedShape
  })

//  max3RunnableGraph.run()

  // same for UniformFanOutShape

  /**
    * Non-uniform fun out shape
    *
    * Processing a bank transactions
    * Trx suspicious if amount > 10_000
    *
    * Streams component for txns
    * - output1: let the transaction go through
    * - output2: suspicious txn ids only
    */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("0934095870289", "Paul", "Jim", 100, new Date),
    Transaction("2304710298634", "Ringo", "Fred", 20000, new Date),
    Transaction("0986298376235", "Smith", "Tom", 200, new Date),
    Transaction("9875875098661", "Jack", "Dick", 9999, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    // step 2 - define SHAPES
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](_.id))

    // step 3 - tie SHAPES
    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    // step 4
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  // step 1
  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2
      val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)

      // step 3
      transactionSource ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcessor
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService

      // step 4
      ClosedShape
    }
  )

  suspiciousTxnRunnableGraph.run()

}
