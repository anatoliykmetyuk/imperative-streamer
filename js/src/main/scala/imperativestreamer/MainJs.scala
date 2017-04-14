package imperativestreamer

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

import scala.scalajs.js.JSApp
import org.scalajs.dom
import dom._
import dom.ext.Ajax
import scalatags.JsDom.all._

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

object MainJs extends JSApp with View with ViewControl {
  def main(): Unit = {
    println("I am alive")
    val rootdir = "/Volumes/TRANSCEND"
    window.onload = { e => requestContents(DirRef(rootdir)) }
  }
}

trait View { this: ViewControl =>
  // Danger of non-exhaustive matches. Especially with Any as an argument, this way compiler warnings are useless (if present at all).
  def view(x: Any): HtmlTag = x match {  // To be handled with type classes. Look at the clutter!
    case ContentsResp(frefs) => view(frefs.map(view))  // Can't have two List[X] in pattern match because of erasure. One more argument for the type classes.
    case refs: List[HtmlTag] => ul(refs.map { r => li(r) }: _*)  // Erasure
    
    case dir: DirRef    => button(onclick := { () => requestContents(dir) })(dir.path)  // Path should be replaced with name
    case dir: ParentRef => button(onclick := { () => requestContents(dir) })("..")
    
    case vid: VideoRef => p(
      i(`class` := "film icon")
    , button(onclick := { () => videoView(vid) })(vid.path)
    )

    case AudioRef(ref) => p(
      i(`class` := "sound icon")
    , button(ref)
    )

    case MiscRef(ref) => p(ref)

    case NullRef      => p("")

    // Changed VideoRef to FileRef. Got non-exhaustive match, the following statement
    // is no longer true. Compiler did not warn, probably because of a match on Any.
    // Problem was because of the parent@ParentRef as opposed to parent@ParentRef(_).
    case (parent@ParentRef(_), VideoRef(refTarget), prev, next) =>
      div(
        view(parent)
      , video( width := 320, height := 240, attr("controls") := true )(
          source(src := s"/vid/$refTarget", `type` := "video/mp4")
        )
      , view(prev)
      , view(next)
      )

    case x => div(s"Cannot render $x")  // This kind of "Cannot do X" errors happen too
                                        // often in cases when it should "obviously"
                                        // can do that. These things should be checked
                                        // on compile-time.
  }
}

trait ViewControl { this: View =>
  def setView(v: Node): Unit = {
    val placeholder = document.getElementById("body-placeholder")
    placeholder.innerHTML = ""
    placeholder.appendChild(v)
  }

  def ajax(req: Request): Future[Response] =
    Ajax.post(url = "/api", data = req.asJson.noSpaces)
      .map { req => decode[Response](req.responseText).right.get }

  def requestContents(ref: FileRef): Unit =  // Can we use DirRef :+: ParentRef here?
    ajax( ContentsReq(ref) ).onComplete {
      case Success(cr@ContentsResp(_)) => setView(view(cr).render)
    }

  def videoView(ref: VideoRef): Unit =
    ajax( NeighborsReq(ref) ).onComplete {
      case Success(NeighborsResp(parent, left, right)) => setView(view( (parent, ref, left, right) ).render)
    }
}
