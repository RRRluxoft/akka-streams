package part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {

  implicit val system = ActorSystem("MaterializingStreams")
  implicit val mt = ActorMaterializer()(system)

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
//  simpleGraph.run()(mt)

  val source = Source(1 to 10)
  val sink: Sink[Int, Future[Int]] = Sink.reduce[Int]((a, b) => a + b)
  val sumFuture: Future[Int] = source.runWith(sink)(mt)

  import system.dispatcher
  sumFuture.onComplete {
    case Success(value) => println(s"The sum of the elements is: $value")
    case Failure(ex) => println(s"The sum of the elements not computed: ${ex.getMessage}")
  }

  // Choosing materialized values:
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink = Sink.foreach[Int](println)

//  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  // equivalent to:
//  simpleSource.viaMat(simpleFlow)(Keep.right)
  val graph: RunnableGraph[Future[Done]] = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  graph.run().onComplete {
    case Success(_) => println(s"Stream process finished")
    case Failure(ex) => println(s"Stream processing failed with: $ex")
  }

  // sugar
  val sum = Source(1 to 10).runWith(Sink.reduce[Int](_ + _))
  // or even better:
  val sum2 = Source(1 to 10).runReduce[Int](_ + _)

  // backwards
  Sink.foreach[Int](println).runWith(Source.single(42)) //== source.to(sink).run()
  // both way
  Flow[Int].map(_ * 2).runWith(simpleSource, simpleSink)

  /**
    * Ex:
    * - return the last element out of source (use Sink.last)
    * - compute the total word of count out ofa stream of sentence
    * - map, fold, reduce
    */
  val sourceLast = Source(10 to 15)
  val last = Sink.last[Int].runWith(sourceLast)
  val f1 = sourceLast.toMat(Sink.last)(Keep.right).run()
  f1.onComplete {
    case Success(value) => println(s"Value is: $value")
    case Failure(ex) => println(s"Failed cause: ${ex.getMessage}")
  }
  // OR
  val f2 = sourceLast.runWith(Sink.last)

  val sentences = List("I love akka streams", "Akka is awesome")
  val sourceSentence = Source(sentences)

  def countWords: (Int, String) => Int = (currentWords, newSentence) =>
    currentWords + newSentence.split(" ").length
  val wordsCountFlow = Flow[String].fold[Int] _//(0)

  val worldCountSink = Sink.fold[Int, String](0)(countWords)

  val g1: Future[Int] = sourceSentence.toMat(worldCountSink)(Keep.right).run()
  val g2: Future[Int] = sourceSentence.runWith(worldCountSink)
  val g3: Future[Int] = sourceSentence.runFold(0)(countWords)

  val splitFlow = Flow[String]
    .fold[Int](0)(countWords)
  val splitFlow2: Flow[String, Int, NotUsed] = wordsCountFlow(0)(countWords)
  val g4 = sourceSentence.via(splitFlow2).toMat(Sink.head)(Keep.right).run()
  val g5: Future[Int] = sourceSentence.viaMat(splitFlow2)(Keep.both).toMat(Sink.head)(Keep.right).run()(mt)
  val g6: Future[Int] = sourceSentence.via(splitFlow2) .runWith(Sink.head)
  val g7: Future[Int] = splitFlow2.runWith(sourceSentence, Sink.head)._2

  println(s"Flow 1 is: ${g1}")
  println(s"Flow 2 is: ${g2}")
  println(s"Flow 3 is: ${g3}")
  println(s"Flow 4 is: ${g4}")
  println(s"Flow 5 is: ${g5}")
  println(s"Flow 6 is: ${g6}")
  println(s"Flow 7 is: ${g7}")

}
