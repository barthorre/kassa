package controllers

import javax.inject.Inject

import actors.WebSocketActor.ReloadMessage
import actors.{UpdatesActor, WebSocketActor}
import akka.actor.ActorSystem
import play.api.Play.current
import play.api._
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, _}
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.{Cursor, QueryOpts}

import scala.concurrent.Future

class MongoApplication @Inject() (mongo: ReactiveMongoApi, system: ActorSystem) extends Controller {

  val logger = Logger(classOf[MongoApplication])

  val updater = system.actorOf(UpdatesActor.props)


  import play.modules.reactivemongo.json._
  import reactivemongo.bson._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val bestellingCollection = mongo.db[JSONCollection]("bestellingen")

  // let's be sure that the collections exists and is capped
  def futureCappedCollection(name: String): Future[JSONCollection] = {
    val db = mongo.db
    val collection = db.collection[JSONCollection](name)

    collection.stats().flatMap {
      case stats if !stats.capped =>
        // the collection is not capped, so we convert it
        logger.info(s"Converting collection ${name} to a capped collection")
        collection.convertToCapped(1024 * 1024, None)
      case _ => Future(collection)
    }.recover {
      // the collection does not exist, so we create it
      case _ =>
        logger.info(s"Creating capped collection ${name}")
        collection.createCapped(1024 * 1024, None)
    }.map { _ =>
      logger.info(s"Capped collection ${name} is available")
      collection
    }
  }


  implicit val jsObjFrame = WebSocket.FrameFormatter.jsonFrame.
    transform[JsObject]({ obj: JsObject => obj: JsValue }, {
    case obj: JsObject => obj
    case js => sys.error(s"unexpected JSON value: $js")
  })

  def watch = WebSocket.using[JsObject] { request =>
    val in = Iteratee.flatten(futureCappedCollection("bestellingen").
      map(collection => Iteratee.foreach[JsObject] { json =>
      println(s"received $json")
      collection.insert(json)
    }))

    // Enumerates the capped collection contents
    val out = {
      val futureEnumerator = futureCappedCollection("bestellingen").map { collection =>
        // so we are sure that the collection exists and is a capped one
        val cursor: Cursor[JsObject] = collection
          // we want all the documents
          .find(Json.obj("status" -> "in verwerking"))
          // the cursor must be tailable and await data
          .options(QueryOpts().tailable.awaitData)
          .cursor[JsObject]()

        // ok, let's enumerate it
        cursor.enumerate()
      }
      Enumerator.flatten(futureEnumerator)
    }

    (in, out)
  }


  def search(key: String, value: String) = Action.async { implicit request =>
    def find: Future[Seq[JsValue]] = {
      bestellingCollection.find(Json.obj(key -> value)).cursor[JsValue]().collect[Seq]()
    }
    logger.info(s"Retrieving information about ${key} ${value}")
    find map { bestellingen =>
      Ok(JsArray(bestellingen))
    }
  }

  def insert() = Action.async { implicit request =>

    println(s"Received bestelling for insert ${request.body.asJson.get}")
    val data: JsValue = request.body.asJson.get
    val jsObj = Json.parse(data.toString()).as[JsObject]
    val result = bestellingCollection.insert(jsObj)
    result map { result =>
      if (result.ok) {
        Ok("Bestelling geplaatst")
      } else {
        Ok(s"Fout bij plaatsen bestelling: ${result.errmsg.getOrElse("")}")
      }
    }
  }

  def markHandled() = Action.async { implicit request =>
    val data: JsValue = request.body.asJson.get
    Logger.info("Receveived bestelling to mark as handled: " + data)
    val jsObj = Json.parse(data.toString()).as[JsObject]
    val oid = jsObj.value.get("_id").get
    val result = bestellingCollection.update(
      BSONDocument("_id" -> oid),
      BSONDocument("$set" -> BSONDocument("status" -> "Verwerkt")),
      upsert = true)
    result map { result =>
      if (result.ok) {
        updater ! ReloadMessage(true)
        Ok("Bestelling afgehandled")
      } else {
        Ok(s"Fout bij het afhandelen van de bestelling: ${result.errmsg.getOrElse("")}")
      }
    }
  }

  def bestellingen = Action.async { implicit request =>
    bestellingCollection.find(Json.obj("status" -> "in verwerking")).cursor[JsValue]().collect[Seq]() map {
      exchanges => Ok(JsArray(exchanges))
    }
  }

  def connect(uuid: String) = WebSocket.acceptWithActor[JsObject, JsObject] { request => out =>
    WebSocketActor.props(updater, out, uuid)
  }

  def categorie(categorie: String) = Action.async { implicit request =>
    bestellingCollection.find(Json.obj("dranken.categorie" -> categorie, "status" -> "in verwerking")).cursor[JsValue]().collect[Seq]() map {
      exchanges => Ok(JsArray(exchanges))
    }
  }

  def watchCategorie(categorie: String) = WebSocket.using[JsObject] { request =>
    val in = Iteratee.flatten(futureCappedCollection("bestellingen").
      map(collection => Iteratee.foreach[JsObject] { json =>
      println(s"received $json")
      collection.insert(json)
    }))

    // Enumerates the capped collection contents
    val out = {
      val futureEnumerator = futureCappedCollection("bestellingen").map { collection =>
        // so we are sure that the collection exists and is a capped one
        val cursor: Cursor[JsObject] = collection
          // we want all the documents
          .find(Json.obj("dranken.categorie" -> categorie, "status" -> "in verwerking"))
          // the cursor must be tailable and await data
            .options(QueryOpts().tailable.awaitData)
          .cursor[JsObject]()

        // ok, let's enumerate it
        cursor.enumerate()
      }
      Enumerator.flatten(futureEnumerator)
    }
    (in, out)
  }
}

