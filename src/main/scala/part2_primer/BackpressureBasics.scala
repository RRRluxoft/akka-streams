package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

object BackpressureBasics extends App {

  implicit val system = ActorSystem("BackpressureBasics")
  implicit val mt = ActorMaterializer()(system)

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int]{ x =>
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  lazy val fastProducer = fastSource.to(slowSink).run()(mt)

  lazy val fastProducerBackPressure =
    fastSource.async
      .to(slowSink)
      .run()

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  lazy val backpressure = fastSource.async
    .via(simpleFlow).async
    .to(slowSink)
    .run()

  /**
    * reaction to backpressure (in order):
    * - try to slow down if psbl
    * - buffer elements until there's more demand
    * - drop down elements from the buffer if it overflows
    * - tear down/kill the whole stream (failure)
    */
  val bufferedFlow = simpleFlow.buffer(10, OverflowStrategy.backpressure) // .runWith(fastSource, slowSink)
  lazy val bufferedDropHead =
    fastSource.async
      .via(bufferedFlow).async
      .to(slowSink)
      .run()

  /**
    * 1-16: nobody is backpressured
    * 17-26: flow will buffered, flow will start dropping at the next element
    * 27-1000: flow will always drop the oldest element
    *   => 991 - 1000 => 992 - 1001 => sink
    */

  // Throttling:
  import scala.concurrent.duration._
  fastSource.throttle(2, 1 second)
    .runWith(Sink.foreach(println))

}
