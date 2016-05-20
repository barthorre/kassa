package actors

import actors.UpdatesActor.{Subscribe, Unsubscribe}
import actors.WebSocketActor.ReloadMessage
import akka.actor.{Actor, ActorRef, Props}
import play.api.Logger

import scala.collection.mutable
import scala.collection.mutable.{HashMap, Set}

/**
 * Created by saggarath on 11/05/16.
 */
class UpdatesActor extends Actor {

  val listeners = new HashMap[String, Set[ActorRef]] with mutable.MultiMap[String, ActorRef]

  override def receive: Receive = {
    case Subscribe(ref, category) => listeners.addBinding(category, ref)
    case Unsubscribe(ref, category) => listeners.removeBinding(category, ref)
    case ReloadMessage(reload) => {
      Logger.info("Received reload message from Mongo to updater")
      for(set <- listeners.values){
        for(listener <- set) {
          listener ! ReloadMessage(reload)
        }
      }
    }
  }
}

object UpdatesActor {
  case class ReloadMessage(reload: Boolean)
  case class Subscribe(ws: ActorRef, category: String)
  case class Unsubscribe(ws: ActorRef, category: String)

  def props() = Props(new UpdatesActor())
}
