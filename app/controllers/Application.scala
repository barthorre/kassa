package controllers

import model.{Categorie, Drank, Ober}
import play.api.Play.current
import play.api._
import play.api.libs.json._
import play.api.mvc._
import scala.collection.JavaConversions._

class Application extends Controller {

  lazy val oberList: List[Ober] = {

    Play.application.configuration.getConfigList("obers") map { confList =>
      val l = for (o <- confList
           ; ober = new Ober(o.getString("naam").getOrElse(""), o.getString("voornaam").getOrElse(""))
      ) yield ober
      Logger.info("Obers werden ingeladen")
      l.toList
    } getOrElse {
        Logger.error("Fout bij het lezen van de ober configuratie")
        List()
    }
  }
   lazy val drankList: List[Drank] = {
    Play.application.configuration.getConfigList("dranken") map { confList =>
      val l = for (d <- confList
        ; drank = new Drank(d.getString("naam").getOrElse(""), Categorie.withName(d.getString("categorie").getOrElse("")), d.getDouble("prijs").getOrElse(0))
      ) yield drank
      l.toList
    } getOrElse {
        Logger.error("Fout bij het lezen van de dranken configuratie")
        List()
    }
   }

  def index = Action {
    println(oberList)
    Ok(views.html.index("Plaats bestelling"))
  }

  def bestellingen = Action {
    Ok(views.html.bestellingen("Bestellingen"))
  }

  def categorie(categorie: String) = Action {
    Ok(views.html.categorie_bestelling(categorie, "Bestellingen"))
  }


  def obers = Action {
    println(Json.toJson(oberList))
    Ok(Json.toJson(oberList))
  }

  def dranken = Action {
    println(Json.toJson(drankList))
    Ok(Json.toJson(drankList))
  }

  implicit val writes = Writes[Ober] { ober =>
    Json.obj(
      "naam" -> JsString(ober.naam),
      "voornaam" -> JsString(ober.voornaam),
      "display" -> JsString(ober.displayName)
    )
  }

  implicit val writesDrank = Writes[Drank] { drank =>
    Json.obj(
      "naam" -> JsString(drank.naam),
      "categorie" -> JsString(drank.categorie.toString),
      "prijs" -> JsNumber(drank.prijs)
    )
  }

}
