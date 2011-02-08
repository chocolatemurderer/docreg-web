package vvv.docreg.model

import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import scala.xml._

class Document extends LongKeyedMapper[Document] with IdPK with ManyToMany {
  def getSingleton = Document

  object key extends MappedString(this, 20)
  object project extends LongMappedMapper(this, Project)
  object title extends MappedString(this, 200)
  object editor extends MappedString(this, 100)
  object subscribers extends MappedManyToMany(Subscription, Subscription.document, Subscription.user, User)

  def revisions = Revision.forDocument(this)
  def revision(version: Long) = Revision.forDocument(this, version)

  def subscriber(user: User) = Subscription.forDocumentandUser(this, user)

  def latest = if (revisions nonEmpty) revisions head else EmptyRevision
  def latest_?(version: Long): Boolean = {
    val r = latest
    r != null && r.version.is == version
  }
  def projectName = project.obj.map(_.name.is) openOr "?"
  def infoLink: String = "/d/" + key.is
}

object Document extends Document with LongKeyedMetaMapper[Document] {
  override def dbIndexes = UniqueIndex(key) :: super.dbIndexes
  override def fieldOrder = List(key, project, title)
  def forKey(key: String): Box[Document] = {
    val xs = findAll(By(Document.key, key))
    if (xs isEmpty) Empty else Full(xs head)
  }
}

object FilteredDocument {
  import vvv.docreg.helper.ProjectSelection
  def search(request: String): List[Document] = searchLike(Document.title, "%" + request + "%")
  def searchLike(field: MappedField[String, Document], value: String): List[Document] = {
    val checked = ProjectSelection.projects.is.toList
    Document.findAll(Like(field, value), In(Document.project, Project.id, ByList(Project.id, checked map ( _.id.is ))), OrderBy(Document.id, Descending), MaxRows(100))
  }
}
