package vvv.docreg.backend

import com.hstx.docregsx.{Document => AgentDocument, Revision => AgentRevision, Approval => AgentApproval, Subscriber => AgentSubscriber, ApprovalStatus => AgentApprovalState, _}
import scala.actors.Actor
import scala.actors.Actor._
import scala.collection.JavaConversions._
import vvv.docreg.model._
import vvv.docreg.model.ApprovalState._
import vvv.docreg.util._

import _root_.net.liftweb.mapper._
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import java.io.IOException

case class Connect()
case class Updated(d: AgentDocument)
case class Reload(d: Document)
case class ApprovalApproved(document: Document, revision: Revision, user: User, state: ApprovalState, comment: String)
case class ApprovalRequested(document: Document, revision: Revision, users: Iterable[User])
case class SubscribeRequested(document: Document, user: User)
case class UnsubscribeRequested(document: Document, user: User)
case class Edit(document: Document, user: User)
case class Unedit(document: Document, user: User)

class Backend extends Actor with Loggable {
  val product = ProjectProps.get("project.name") openOr "drw"
  val version = ProjectProps.get("project.version") openOr "0.0"
  val reconciler = new Reconciler(this)
  var agent: Agent = _
  def act() {
    loop {
      react {
        case Connect() => 
          logger.info("Starting " + product + " v" + version + " " + java.util.TimeZone.getDefault.getDisplayName)
          agent = new Agent(version, Backend.server, product)
          val library = new FileList(Backend.server, agent)
          library.addUpdateListener(new UpdateListener() {
            def updated(ds: java.util.List[AgentDocument]) = ds.foreach(Backend.this ! Updated(_))
            def updated(d: AgentDocument) = Backend.this ! Updated(d)
          })
        case Updated(d) => 
          Document.forKey(d.getKey) match {
            case Full(document) => updateDocument(document, d)
            case _ => createDocument(d)
          }
        case Reload(d) =>
          updateRevisions(d)
          updateSubscriptions(d)
          applyApprovals(d, agent.loadApprovals(d.key))
        case ApprovalApproved(d, r, user, state, comment) =>
          val done = agent.approval(r.filename, 
            user.displayName, 
            user.email.is,
            state match {
              case ApprovalState.approved => AgentApprovalState.Approved
              case ApprovalState.notApproved => AgentApprovalState.NotApproved
              case _ => AgentApprovalState.Pending
            },
            comment,
            product,
            user.email.is)
          if (done) logger.info("Approval processed") else logger.warn("Approval rejected for " + r + " by " + user + " to " + state)
        case ApprovalRequested(d, r, users) =>
          users foreach (this ! ApprovalApproved(d, r, _, ApprovalState.pending, ""))
        case SubscribeRequested(d, user) =>
          if(agent.subscribe(d.latest.filename, user.email))
            logger.info("Subscribe request accepted")
          else
            logger.warn("Subscribe request rejected for " + d + " by " + user)
        case UnsubscribeRequested(d, user) =>
          if(agent.unsubscribe(d.latest.filename, user.email))
            logger.info("Unsubscribe request accepted")
          else
            logger.warn("Unsubscribe request rejected for " + d + " by " + user)
        case Edit(d, user) =>
          agent.edit(d.latest.filename, user.displayName)
          logger.info("Edit request sent")
        case Unedit(d, user) =>
          try {
            agent.unedit(d.latest.filename, user.displayName)
          } catch {
            case e: IOException => logger.info("Unedit request sent")
          }
        case m @ _ => logger.warn("Unrecognised message " + m)
      }
    }
  }
  
  private def projectWithName(name: String) = {
    val existing = Project.forName(name) 
    if (existing == null) {
      val project = Project.create
      project.name(name)
      project.save
      project
      // TODO notify new project? Or do it as notification on save.
    } else {
      existing
    }
  }

  private def createDocument(d: AgentDocument) {
    try {
      val document = Document.create
      assignDocument(document, d)
      document.save

      agent.loadRevisions(d).foreach{createRevision(document, _)}
      applyApprovals(document, agent.loadApprovals(d))

      agent.loadSubscribers(d).foreach{createSubscription(document, _)}

      
      DocumentServer ! DocumentAdded(document)
    } catch {
      case e: java.lang.NullPointerException => logger.error("Exception " + e + " with " + d.getKey); e.printStackTrace
    }
  }

  private def createRevision(document: Document, r: AgentRevision): Revision = {
    val revision = Revision.create
    revision.document(document)
    assignRevision(revision, r)
    revision.save
    revision
  }

  private def applyApprovals(document: Document, approvals: Iterable[AgentApproval]) = approvals foreach { a =>
    // The agent returns a single approval item per revision/user pair, so no need to cull.
    Revision.forDocument(document, a.getVersion) match {
      case Full(revision) => 
        val user = User.forEmailOrCreate(a.getApproverEmail) openOr null
        val approval = Approval.forRevisionBy(revision, user) match {
          case Full(a) => a
          case _ => Approval.create.revision(revision).by(user)
        } 
        approval.state(ApprovalState.parse(a.getStatus.toString))
        approval.date(a.getDate)
        approval.comment(a.getComment)
        approval.save
      case _ => 
        logger.warn("Approval found with no matching revision: " + a)
    }
  }

  private def createSubscription(document: Document, s: AgentSubscriber): Subscription = {
    val subscription = Subscription.create
    subscription.document(document)
    assignSubscription(subscription, s)
    subscription.save
    subscription
  }

  private def updateDocument(document: Document, d: AgentDocument) {
    if (document.latest_?(d.getVersion.toLong)) {
      reconciler ! PriorityReconcile(document)
    } else {
      updateRevisions(document)
      updateSubscriptions(document)
    }
    
    assignDocument(document, d)
    if (document.dirty_?) { 
      document.save
      DocumentServer ! DocumentChanged(document)
    }
  }

  private def assignDocument(document: Document, d: AgentDocument) {
    document.key(d.getKey)
    document.project(projectWithName(d.getProject))
    document.title(d.getTitle)
    document.editor(d.getEditor)
  }

  private def assignRevision(revision: Revision, r: AgentRevision) {
    revision.version(r.getVersion)
    revision.filename(r.getFilename)
    revision.author(r.getAuthor)
    revision.date(r.getDate)
    revision.comment(r.getComment)
  }

  private def assignSubscription(subscription: Subscription, s: AgentSubscriber) {
    User.forEmail(s.getSubscriberEmail) match {
      case Full(u) =>
        subscription.user(u)
      case _ =>
        Empty
    }
  }

  private def updateRevisions(document: Document) {
    agent.loadRevisions(document.key).foreach { r =>
      document.revision(r.getVersion) match {
        case Full(revision) =>
          assignRevision(revision, r)
          if (revision.dirty_?) {
            revision.save
            DocumentServer ! DocumentChanged(document)
          }
        case _ => 
          val latest = createRevision(document, r)
          DocumentServer ! DocumentRevised(document, latest)
      }
    }
  }

  private def updateSubscriptions(document: Document) {
    agent.loadSubscribers(document.key).foreach { s =>
      document.subscriber(User.forEmailOrCreate(s.getSubscriberEmail) openOr null) match {
        case Full(subscription) =>
          assignSubscription(subscription, s)
          if (subscription.dirty_?) {
            subscription.save
          }
        case _ =>
          val latest = createSubscription(document, s)
      }
    }
  }

}

object Backend extends Backend {
  val server: String = Props.get("backend.server") openOr "shelob" // shelob.gnet.global.vpn?
}
