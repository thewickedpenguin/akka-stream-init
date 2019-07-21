import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.softwaremill.sttp._
import io.circe.generic.auto._
import io.circe.parser
import io.circe.syntax._

import scala.concurrent.Future

object WithFutures extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  def getData(): Future[String] = Future {
    val x = sttp.get(uri"http://localhost:3000/users?_limit=100")

    implicit val backend = HttpURLConnectionBackend()
    val response = x.send()
    response.body.right.get
  }

  val futures = (1 to 100).map {_ =>
    getData()
  }

  val source: Source[String, NotUsed] = Source(futures.toList).mapAsync(1)(identity)
  def sink(): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get("results.text"))) (Keep.right)

  val done: Future[IOResult] = source.runWith(sink)(materializer)
  done.onComplete(_ => system.terminate())

}

case class RedditComment(body: String, controversiality: Int, ups: Int, author: String)

object ControversialDownvotes extends App {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get("reddit.txt"))
  val sink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(Paths.get("results.txt"))

  val parse: Flow[ByteString, RedditComment, NotUsed] =
    Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 8196, allowTruncation = true))
      .map( s => {
        parser.decode[RedditComment](s.utf8String) match {
          case Right(comment) => Some(comment)
          case Left(err) => {
            println(err.getMessage)
            None
          }
        }
      })
      .collect { case Some(comment) => comment}

  val filterComments: Flow[RedditComment, RedditComment, NotUsed] =
    Flow[RedditComment]
      .filter(_.ups < 0)
      .filter(_.controversiality > 0)

  val toByteString: Flow[RedditComment, ByteString, NotUsed] =
    Flow[RedditComment]
    .map(comment => ByteString(s"${comment.asJson.noSpaces}\n"))

  val composedFlow: Flow[ByteString, ByteString, NotUsed] =
    parse
      .via(filterComments)
      .via(toByteString)

  val runnableGraph: RunnableGraph[Future[IOResult]] =
    source.via(composedFlow).toMat(sink)(Keep.right)

  runnableGraph.run().foreach { _ =>
    system.terminate()
  }
}

