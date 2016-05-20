package actors

import actors.UpdatesActor.{Subscribe, Unsubscribe}
import actors.WebSocketActor.ReloadMessage
import akka.actor.{Actor, ActorRef, Props}
import play.api.Logger
import play.api.libs.json.Json

/**
 * Created by saggarath on 19/04/16.
 */
class WebSocketActor(updater: ActorRef, out: ActorRef, category: String) extends Actor {
    override def preStart = {
    updater ! Subscribe(self, category)
  }

  override def postStop = {
    updater ! Unsubscribe(self, category)
  }


  override def receive: Receive = {

    case ReloadMessage(reload) => {
      Logger.info("Received reload message in websocket Actor")
      out ! (Json.obj("reload" -> reload))
    }
  }
}

object WebSocketActor {
  case class ReloadMessage(reload: Boolean)
  def props(updater: ActorRef, out: ActorRef, uuid: String) = Props(new WebSocketActor(updater, out, uuid))
}