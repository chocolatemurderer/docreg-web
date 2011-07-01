package vvv.docreg.snippet

import _root_.scala.xml._
import _root_.net.liftweb._
import http._
import S._
import SHtml._
import common._
import util._
import Helpers._
import js._
import JsCmds._
import _root_.net.liftweb.http.js.jquery.JqJsCmds._
import _root_.net.liftweb.http.js.JE.JsRaw
import vvv.docreg.model.{FilteredDocument,Document}
import vvv.docreg.helper.ProjectSelection
import vvv.docreg.comet._

class Search extends Loggable with ProjectSelection {
  object searchInput extends SessionVar("")
  def input(xhtml: NodeSeq): NodeSeq = {
    bind("search", xhtml, 
      "form" -> form _)
  }
  def form(xhtml: NodeSeq): NodeSeq = {
    bind("search", xhtml,
      "text" -> JsCmds.FocusOnLoad(SHtml.text(searchInput.is, s => searchInput(s)) % ("style" -> "width: 250px")),
      "submit" -> SHtml.submit("Search", () => S.redirectTo("search")))
  }
  def bindResults(in: NodeSeq): NodeSeq = {
    import vvv.docreg.util.StringUtil._
    // todo better parsing of search string, from 
    val keyMatches = results(in, FilteredDocument.searchLike(Document.key, prePadTo(searchInput.is, 4, '0')))
    val titleMatches = results(in, FilteredDocument.searchLike(Document.title, "%" + searchInput.is + "%"))
    keyMatches ++ titleMatches
  }
  var html: NodeSeq = NodeSeq.Empty
  def results(in: NodeSeq): NodeSeq = {
    html = in
    bindResults(in)
  }
  def results(in: NodeSeq, ds: List[Document]): NodeSeq = ds match {
    case Nil => Text("")
    case xs => bind("search", in, "result" -> (n => items(n, xs)))
  }
  def items(in: NodeSeq, ds: List[Document]): NodeSeq = {
    ds.flatMap(d => bind("doc", in,
        "project" -> d.projectName,
        "author" -> d.latest.author,
        "key_link" -> <a href={d.latest.link}>{d.key}</a>,
        "date" -> d.latest.date,
        "title" -> <a href={d.infoLink}>{d.title}</a>))
  }
  override def projectSelectionUpdate(): JsCmd = {
    CurrentLog.foreach(_ ! ReloadLog())
    Replace("search_results", bindResults(html))
  }
}
