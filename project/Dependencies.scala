import sbt.*

object Dependencies {
  object version {
    val profig: String = "3.4.14"
    val scribe: String = "3.13.4"
    val fabric: String = "1.14.4"
    val rabbitMQ: String = "5.21.0"
    val lightDB: String = "0.11.0"

    val scalaTest: String = "3.2.18"
    val catsEffectTesting: String = "1.5.0"
  }

  val profig: ModuleID = "com.outr" %% "profig" % version.profig
  val scribeSLF4J: ModuleID = "com.outr" %% "scribe-slf4j" % version.scribe
  val scribeCats: ModuleID = "com.outr" %% "scribe-cats" % version.scribe
  val scribeFile: ModuleID = "com.outr" %% "scribe-file" % version.scribe
  val fabric: ModuleID = "org.typelevel" %% "fabric-io" % version.fabric
  val rabbitMQ: ModuleID = "com.rabbitmq" % "amqp-client" % version.rabbitMQ
  val lightDB: ModuleID = "com.outr" %% "lightdb-all" % version.lightDB

  val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % version.scalaTest % Test
  val catsEffectTesting: ModuleID = "org.typelevel" %% "cats-effect-testing-scalatest" % version.catsEffectTesting % Test
}