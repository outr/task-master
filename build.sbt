// Scala versions
val scala213 = "2.13.14"
val scala3 = "3.3.3"
val scala2 = List(scala213)
val allScalaVersions = scala3 :: scala2

// Variables
val org: String = "com.outr"
val projectName: String = "task-master"
val githubOrg: String = "outr"
val email: String = "matt@matthicks.com"
val developerId: String = "darkfrog"
val developerName: String = "Matt Hicks"
val developerURL: String = "https://matthicks.com"

name := projectName
ThisBuild / organization := org
ThisBuild / version := "1.0.0-SNAPSHOT"
ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := allScalaVersions
ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")
ThisBuild / javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeProfileName := org
ThisBuild / licenses := Seq("MIT" -> url(s"https://github.com/$githubOrg/$projectName/blob/master/LICENSE"))
ThisBuild / sonatypeProjectHosting := Some(xerial.sbt.Sonatype.GitHubHosting(githubOrg, projectName, email))
ThisBuild / homepage := Some(url(s"https://github.com/$githubOrg/$projectName"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/$githubOrg/$projectName"),
    s"scm:git@github.com:$githubOrg/$projectName.git"
  )
)
ThisBuild / developers := List(
  Developer(id=developerId, name=developerName, email=email, url=url(developerURL))
)

ThisBuild / resolvers += Resolver.mavenLocal
ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

ThisBuild / outputStrategy := Some(StdoutOutput)
ThisBuild / Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
ThisBuild / fork := true

def dep: Dependencies.type = Dependencies

lazy val root = project
  .in(file("."))
  .aggregate(core, task, scheduler, workflow)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val core = project
  .in(file("core"))
  .settings(
    libraryDependencies ++= Seq(
      dep.profig, dep.scribeSLF4J, dep.scribeCats, dep.fabric, dep.rabbitMQ, dep.lightDB,
      dep.scalaTest, dep.catsEffectTesting
    )
  )

lazy val task = project
  .in(file("task"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Seq(
      dep.scalaTest, dep.catsEffectTesting
    )
  )

lazy val scheduler = project
  .in(file("scheduler"))
  .dependsOn(core % "compile->compile;test->test", task)
  .settings(
    libraryDependencies ++= Seq(
      dep.scalaTest, dep.catsEffectTesting
    )
  )

lazy val workflow = project
  .in(file("workflow"))
  .dependsOn(core % "compile->compile;test->test", scheduler)
  .settings(
    libraryDependencies ++= Seq(
      dep.scalaTest, dep.catsEffectTesting
    )
  )

lazy val bench = project
  .in(file("bench"))
  .dependsOn(workflow)
  .settings(
    fork := true
  )