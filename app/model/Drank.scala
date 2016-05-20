package model

/**
 * Created by saggarath on 31/03/16.
 */
case class Drank(naam: String, categorie: Categorie.Value, prijs: Double) {

}

object Categorie extends Enumeration {
  val Frisdrank, Keuken, Alcoholisch = Value
}