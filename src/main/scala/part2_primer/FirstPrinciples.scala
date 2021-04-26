package part2_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {

  implicit val system = ActorSystem("FirstPrinciples")
  implicit val mt = ActorMaterializer()(system)

  //sources
  val source = Source(1 to 10)
  // sink
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
  graph.run()(mt)

  // flows transform elements
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

  sourceWithFlow.to(sink).run()
  source.to(flowWithSink).run()
  source.via(flow).to(sink).run()

  // null NOT allowed
//  val illegalSource = Source.single[String](null)
//  illegalSource.to(Sink.foreach(println)).run()
  // use Option instead

  // various kind of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1))

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.fromFuture(Future(5))

  // sink
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and close the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  // flows
  val mapFlow = Flow[Int].map(x => x * 2)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // Not have flatMap

  // source -> flow -> flow -> ... -> sink
  println("source -> flow -> flow -> ... -> sink")
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  doubleFlowGraph.run()

  // syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2)
  // equivalent to: Source(1 to 10).via(Flow[Int].map(_ * 2))

  // run streams directly:
  mapSource.runForeach(println)
  // equivalent to: mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = components

  /**
    * Ex: create a stream that takes the names of persons,
    * then will keep the first 2 names with length > 5 chars
   */
  println("=== Names ===")
  val names = List("David", "Bob", "Martin", "Daniel", "Sid")
  val nameSource = Source[String](names)

  val longNameFlow: Flow[String, String, NotUsed] = Flow[String].filter(_.length > 5)
  val firstTwoFlow: Flow[String, String, NotUsed] = Flow[String].take(2)
  val nameSink = Sink.foreach[String](println)
  nameSource
    .via(longNameFlow)
    .via(firstTwoFlow)
    .to(nameSink)
    .run()
  // or:
  nameSource
    .filter(_.length > 5)
    .take(2)
    .runForeach(println)
}
