import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.softwaremill.sttp._

import scala.concurrent.Future

object Main extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  implicit val ec = system.dispatcher

  def getData(): Future[String] = Future {
    val x = sttp.get(uri"http://localhost:3000/users?_limit=100")

    implicit val backend = HttpURLConnectionBackend()
    val response = x.send()
    response.body.right.get
  }

  val source: Source[String, NotUsed] = Source.fromFuture(getData())
  def sink(): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get("abc.text"))) (Keep.right)

  val done: Future[IOResult] = source.runWith(sink)(materializer)
  done.onComplete(_ => system.terminate())

}