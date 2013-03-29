package lila.i18n

import play.api.mvc.Request
import play.api.data._
import play.api.data.Forms._
import org.joda.time.DateTime
import play.api.libs.concurrent.Execution.Implicits._

// TODO captcha
// import site.Captcha

final class DataForm(
    keys: I18nKeys /*, captcher: Captcha */ ) {

  val translation = Form(mapping(
    "author" -> optional(nonEmptyText),
    "comment" -> optional(nonEmptyText),
    "gameId" -> nonEmptyText,
    "move" -> nonEmptyText
  )(TransMetadata.apply)(TransMetadata.unapply).verifying(
      "Not a checkmate",
      data ⇒ true //captcher get data.gameId valid data.move.trim.toLowerCase
    ))

  // def translationWithCaptcha = translation -> captchaCreate

  // def captchaCreate: Captcha.Challenge = captcher.create

  def process(code: String, metadata: TransMetadata, data: Map[String, String]): Funit = {
    val messages = (data mapValues { msg ⇒
      msg.some map sanitize filter (_.nonEmpty)
    }).toList collect {
      case (key, Some(value)) ⇒ key -> value
    }
    val sorted = (keys.keys map { key ⇒
      messages find (_._1 == key.key)
    }).flatten
    messages.nonEmpty.fold(for {
      id ← TranslationRepo.nextId
      translation = Translation(
        id = id,
        code = code,
        text = sorted map {
          case (key, trans) ⇒ key + "=" + trans
        } mkString "\n",
        author = metadata.author,
        comment = metadata.comment,
        createdAt = DateTime.now)
      _ ← translationTube |> { implicit tube ⇒
        lila.db.api.$insert(translation)
      }
    } yield (), funit)
  }

  def decodeTranslationBody(implicit req: Request[_]): Map[String, String] = req.body match {
    case body: play.api.mvc.AnyContent if body.asFormUrlEncoded.isDefined ⇒
      (body.asFormUrlEncoded.get collect {
        case (key, msgs) if key startsWith "key_" ⇒ msgs.headOption map { key.drop(4) -> _ }
      }).flatten.toMap
    case body ⇒ {
      println("Can't parse translation request body: " + body)
      Map.empty
    }
  }

  private def sanitize(message: String) = message.replace("""\n""", " ").trim
}

private[i18n] case class TransMetadata(
  author: Option[String],
  comment: Option[String],
  gameId: String,
  move: String)
