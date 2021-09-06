package part4_techniques

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object IntegratingWithActors extends App {

  implicit val system = ActorSystem("IntegratingWithActors")
  implicit val materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string: $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  val numberSource: Source[Int, NotUsed] = Source(1 to 10)

  // actor as a flow
  implicit val timeout = Timeout(2 seconds)
  val actorBasedFlow = Flow[Int]
    .ask[Int](parallelism = 4)(simpleActor)

  val res: Future[Seq[Int]] = numberSource
    .via(actorBasedFlow)
    .toMat(Sink.collection/*foreach[Int](println)*/)(Keep.right)
    .run()

  res.foreach(i => println(s"[actorBasedFlow]: $i"))

//  numberSource.via(actorBasedFlow).to(Sink.ignore).run()
//  numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run() // EQUIVALENT !


  /**
    * Actor as a source
   */
  val actorPoweredSource = Source.actorRef(bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
  val materializedActorRef: ActorRef =
    actorPoweredSource
      .to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number")))
      .run()

  materializedActorRef ! 10

  // terminating the stream
  materializedActorRef ! akka.actor.Status.Success("complete")

  /*
  Actor as a destination/sink
  - an init message
  - an ack message to confirm the reception
  - a complete message
  - a function to generate a message in case the stream throws an exception
 */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info(s"Stream initialized")
        sender() ! StreamAck

      case StreamComplete =>
        log.info(s"Stream complete")
        context.stop(self)

      case StreamFail(ex) =>
        log.warning(s"Stream failed: $ex")

      case message =>
        log.info(s"Message $message has come to its final resting point.")
        sender() ! StreamAck
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "destinationActor")
  val actorPoweredSink =
    Sink.actorRefWithAck[Int](
      destinationActor,
      onInitMessage = StreamInit,
      onCompleteMessage = StreamComplete,
      ackMessage = StreamAck,
      onFailureMessage = throwable => StreamFail(throwable)
    )

  Source(10 to 20)
    .toMat(actorPoweredSink)(Keep.right)
    .run()

  //  Sink.actorRef() not recommended, unable to backpressure
}
