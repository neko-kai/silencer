
name := "silencer"

val saveTestClasspath = taskKey[File](
  "Saves test classpath to a file so that it can be used by embedded scalac in tests")

pgpPublicRing := file("./travis/local.pubring.asc")
pgpSecretRing := file("./travis/local.secring.asc")
pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray)

credentials in Global += Credentials(
  "Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  sys.env.getOrElse("SONATYPE_USERNAME", ""),
  sys.env.getOrElse("SONATYPE_PASSWORD", "")
)

version in ThisBuild :=
  sys.env.get("TRAVIS_TAG").filter(_.startsWith("v")).map(_.drop(1)).getOrElse("1.4-SNAPSHOT")

val commonSettings = Seq(
  organization := "com.github.ghik",
  resolvers += "scala-integration" at "https://scala-ci.typesafe.com/artifactory/scala-integration/",
  scalaVersion := "2.13.1-bin-34d3acb",
  crossVersion := CrossVersion.full,
  crossScalaVersions := Seq("2.11.12", "2.12.8", "2.12.9", "2.12.10", "2.13.0", scalaVersion.value),
  unmanagedSourceDirectories in Compile ++= {
    (unmanagedSourceDirectories in Compile).value.map { dir =>
      val sv = scalaVersion.value
      val is130 = sv == "2.13.0" // use 2.12 version for 2.13.0, reporters changed in 2.13.1
      CrossVersion.partialVersion(sv) match {
        case Some((2, n)) if n < 13 || is130 => file(dir.getPath ++ "-2.12-")
        case _                               => file(dir.getPath ++ "-2.13+")
      }
    }
  },
  projectInfo := ModuleInfo(
    nameFormal = "Silencer",
    description = "Scala compiler plugin for annotation-based warning suppression",
    homepage = Some(url("https://github.com/ghik/silencer")),
    startYear = Some(2015),
    licenses = Vector(
      "The Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
    ),
    organizationName = "ghik",
    organizationHomepage = Some(url("https://github.com/ghik")),
    scmInfo = Some(ScmInfo(
      browseUrl = url("https://github.com/ghik/silencer.git"),
      connection = "scm:git:git@github.com:ghik/silencer.git",
      devConnection = Some("scm:git:git@github.com:ghik/silencer.git")
    )),
    developers = Vector(
      Developer("ghik", "Roman Janusz", "romeqjanoosh@gmail.com", url("https://github.com/ghik"))
    ),
  )
)

val subprojectSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  pomIncludeRepository := { _ => false },
)

lazy val silencer = (project in file(".")).aggregate(`silencer-lib`, `silencer-plugin`)
  .settings(commonSettings: _*)
  .settings(
    publishArtifact := false,
    PgpKeys.publishSigned := {}
  )

lazy val `silencer-lib` = project
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Test
    )
  )

lazy val `silencer-plugin` = project.dependsOn(`silencer-lib`)
  .settings(subprojectSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      if (scalaBinaryVersion.value == "2.13")
        "org.scalatest" %% "scalatest" % "3.1.0-SNAP13" % Test
      else
        "org.scalatest" %% "scalatest" % "3.0.8-RC5" % Test
    ),
    saveTestClasspath := {
      val result = (classDirectory in Test).value / "embeddedcp"
      IO.write(result, (fullClasspath in `silencer-lib` in Test).value.map(_.data.getAbsolutePath).mkString("\n"))
      result
    },
    (test in Test) := (test in Test).dependsOn(saveTestClasspath).value,
    fork in Test := true,
    baseDirectory in Test := (baseDirectory in ThisBuild).value,
  )
