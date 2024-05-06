package taskmaster.task

sealed trait TypedTaskResult[+Result]

object TypedTaskResult {
  case class Success[Result](result: Result, continue: Boolean = true) extends TypedTaskResult[Result]

  case class Failure(message: String, permanent: Boolean) extends TypedTaskResult[Nothing]
}