package imperativestreamer

import scala.collection.JavaConverters._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.io.File
import java.net.InetSocketAddress
import com.sun.net.httpserver._

import org.apache.commons.io.{IOUtils, FileUtils}
import org.apache.commons.codec.net.URLCodec

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._


object MainJvm extends App {
  val server = HttpServer.create(new InetSocketAddress(8080), 0)

  def serveFile(path: String, contentType: String = "text/html"): HttpHandler = t => {
    val file = new File(s"assets/$path")
    val fileIs = FileUtils.openInputStream(file)
    val os = t.getResponseBody
    t.sendResponseHeaders(200, 0)
    IOUtils.copy(fileIs, os)
    os.close()
  }

  def ext(f: String): String = f.reverse.takeWhile(_ != '.').reverse
  val videoExts = Set("mp4", "m4v")
  val audioExts = Set("mp3")

  val apiHandler: HttpHandler = t => {
    if (t.getRequestMethod.toUpperCase != "POST") throw new RuntimeException("Wrong Method")

    val req = IOUtils.toString(t.getRequestBody)
    val reqModel = decode[Request](req)

    val respModel: Response = reqModel.right.get match {  // Non exhaustive match. Got exception on runtime because failed to handle NeighborsReq.
      case ContentsReq(ref) =>  // Again non exhaustive match: matched against ContentsReq(DirRef), forgot ContentsReq(ParentRef)
        val refDir: File = new File(ref.path)

        val parent  : ParentRef     = ParentRef(refDir.getParent)
        val contents: List[FileRef] = refDir.listFiles.toList
          .map { f =>
            val path = f.getAbsolutePath
            if (f.isDirectory) DirRef(path)
            else ext(f.getName) match {
              case e if videoExts(e) => VideoRef(path)
              case e if audioExts(e) => AudioRef(path)
              case _ => MiscRef(path)
            }
          }

        println(contents)
        ContentsResp(parent :: contents)
      
      case NeighborsReq(fr) =>
        val f = new File(fr.path)
        val files = f.getParentFile.listFiles.toList
          .filter { f => videoExts(ext(f.getName)) }
          
        (files.sliding(3, 1)
          .collect {
            // Case for implicit conversions?
            // Boilerplate with NeighborsResp always containing ParentRef. Case for Shapeless Merge of case classes?
            case l :: m :: r :: Nil if m == f => NeighborsResp(ParentRef(f.getParent), VideoRef(l.getAbsolutePath), VideoRef(r.getAbsolutePath))
          }
          .toList.headOption match {
            case resp@Some(_) => resp
            case None => files.sliding(2, 1)  // Non-obvious pagination logic. Any ways to improve?
              .collect {
                case l :: r :: Nil if l == f => NeighborsResp(ParentRef(f.getParent), NullRef, VideoRef(r.getAbsolutePath))
                case l :: r :: Nil if r == f => NeighborsResp(ParentRef(f.getParent), VideoRef(l.getAbsolutePath), NullRef)
              }
              .toList.headOption
          })
          .getOrElse(NeighborsResp(ParentRef(f.getParent), NullRef, NullRef))

    }
    val resp = respModel.asJson.noSpaces

    val os = t.getResponseBody
    t.sendResponseHeaders(200, resp.length)
    IOUtils.write(resp, os)
    os.close()
  }

  // Broken pipe exception when playing the video, not reported anywhere. Seriously?!
  // IndexOutOfBounds exception when reading the file... Also unreported...
  // Synchronous handler blocks the entire server.
  val videoHandler: HttpHandler = t => Future { try {
    // Get file name
    val filename = new URLCodec().decode( t.getRequestURI.toString.drop("/vid/".length) ) // This way, the compiler won't alert us when "/vid/" path changes; not DRY approach
    val file = new File(filename)
    println(file.exists())
    val available = file.length()

    // Range
    println(t.getRequestHeaders)
    val range = t.getRequestHeaders
      .get("Range").asScala.head
      .drop("bytes=".length)
      .split("-").toList
      .map(_.toInt)
    println(range)
    println(available)

    val (from, to) = range match {
      case from :: to :: Nil => from -> math.min(available - 1, to)
      case from :: Nil       => from -> (available - 1)
    }
    println(from + " to " + to)

    // Read that file into a byte array
    val length = (to - from).toInt + 1
    println(s"Reading length: $length, offset: $from")

    // val is = FileUtils.openInputStream(file)
    // is.skip(from)
    // val buff = IOUtils.toByteArray(is, length)
    // is.close()  // Without finally

    // Headers
    // Content-Type  video/mp4
    // Accept-Ranges bytes
    // Content-Range bytes 0-511830134/511830135
    val headers = t.getResponseHeaders
    headers.put("Content-Type" , List("video/mp4").asJava)
    headers.put("Accept-Ranges", List("bytes").asJava)
    headers.put("Content-Range", List(s"bytes $from-$to/$available").asJava)
    t.sendResponseHeaders(206, 0)

    // Flush array to the OS
    val os = t.getResponseBody
    val is = FileUtils.openInputStream(file)
    // IOUtils.writeChunked(buff, os)
    IOUtils.copyLarge(is, os, from, length)
    is.close()
    os.close()
  } catch { case e: Throwable => e.printStackTrace() } }

  server.createContext("/", serveFile("html/index.html"))
  server.createContext("/js/application.js", serveFile("js/application.js", "text/javascript"))
  server.createContext("/js/application.js.map", serveFile("js/application.js.map", "text/plain"))
  server.createContext("/api", apiHandler)
  server.createContext("/vid/", videoHandler)
  server.start()
}
