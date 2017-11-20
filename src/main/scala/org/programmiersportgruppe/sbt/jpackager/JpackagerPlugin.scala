package org.programmiersportgruppe.sbt.jpackager

import org.json4s._
import org.json4s.native.JsonMethods._

import java.io.{FileOutputStream, OutputStreamWriter}

import sbt.{Artifact, Attributed, ModuleID, _}
import sbt.Keys._
import sbt.plugins.JvmPlugin

object JpackagerPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = JvmPlugin

  object autoImport {
    val generateManifast: TaskKey[Seq[sbt.File]] = taskKey[Seq[File]]("A task that is automatically imported to the build")
  }

  import autoImport._

  override lazy val projectSettings = Seq(

    generateManifast := Def.task {

      val classpath: Seq[Attributed[File]] =
        Classpaths.managedJars(Compile, classpathTypes.value, update.value)
      val logger = sLog.value

      val deps: Seq[ModuleID] = classpath.flatMap { entry =>
        for {
          art: Artifact <- entry.get(artifact.key)
          mod: ModuleID <- entry.get(moduleID.key)
        } yield {
          logger.debug(
            s"""[Lock] "${mod.organization}" % "${mod.name}" % "${mod.revision}""""
          )
          mod
        }
      }
      generateManifest((resourceManaged in Compile).value, deps, mainClass.value.getOrElse(""))
    }.value

  )

  override lazy val buildSettings = Seq()

  override lazy val globalSettings = Seq()

  def generateManifest(base: File, deps: Seq[ModuleID], mainClassName: String): Seq[File] = {

    val path = new File(base, "j-manifest.json")
    base.mkdirs()
    val writer = new OutputStreamWriter(new FileOutputStream(path), "UTF-8")

    val root = JObject(
      "mainClass" -> JString(mainClassName),
      "dependencies" -> JArray(deps.map(moduleID =>
        JObject(
          "groupId" -> JString(moduleID.organization),
          "artifactId" -> JString(moduleID.name),
          "version" -> JString(moduleID.revision),
          "configurations" -> JString(moduleID.configurations.getOrElse(null))
        )
      ).toList)

    )
    writer.write(pretty(render(root)))
    writer.close()
    Seq(path)
  }
}