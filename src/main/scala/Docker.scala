package jp.saisse.docker.command

import java.io.File
import scala.sys.process.Process
import java.nio.file.Files
import java.nio.file.StandardCopyOption

case class Docker(name: String, tag: String, directory: File) {
  val copyOption = StandardCopyOption.REPLACE_EXISTING

  def build(envDir: File, files: Seq[File] = Seq()): Unit = {
    setupDockerContent(envDir, files)
    exec(s"bash build.sh $tag", Some(directory))
  }

  def rebuild(envDir: File, files: Seq[File] = Seq()): Unit = {
    val id = listContainerId(tag)
    build(envDir, files)
    reloadContainer(id)
  }

  private def reloadContainer(id: Option[String]): Unit = {
    println(s"reload $tag container at ${directory.getAbsolutePath}")
    if(isMac()) {
      val x = (Process("boot2docker status") !!) match {
        case "running\n" =>
        case _ => {
          exec("boot2docker up")
        }
      }
    }
    stop(id)
    exec(s"bash daemon.sh $tag", Some(directory))
  }

  def stop(id: Option[String]): Unit = {
    println(s"stop $tag container at ${directory.getAbsolutePath}")
    id.foreach(id => {
      exec(s"docker stop $id")
      exec(s"docker wait $id")
    })
  }

  private def setupDockerContent(envDir: File, files: Seq[File] = Seq()): Unit = {
    val contentDir = new File(directory, "content")
    if(!contentDir.exists) {
      Files.createDirectory(contentDir.toPath)
    }
    val contents = (new File(envDir, "docker")).listFiles ++ files
    contents.foreach(f => {
      println(s"copy ${f.getAbsolutePath} to ${contentDir.getAbsolutePath}")
      Files.copy(f.toPath, new File(contentDir, f.getName()).toPath, copyOption)
    })
  }

  def containerExec(command: String): Unit = {
    val id = listContainerId(tag).get
    exec(s"docker exec -t -it $id $command")
  }

  def cp(containerPath: String, localPath: String): Unit = {
    val id = listContainerId(tag).get
    exec(s"docker cp $id:$containerPath $localPath")
  }

  private def exec(command: String, dir: Option[File] = None): Int = {
    println(s"exec: $command")
    val result = ((dir.fold(Process(command))(d => Process(command, d))) !)
    if(result != 0) {
       throw new Exception(s"command execution failed. $command")
    }
    result
  }

  private def listContainerId(tag: String): Option[String] = {
    val line = (Process("docker ps") !!).split("\n").find(l => l.contains(tag))
    line.map(l => l.split("[ ]")(0))
  }

  private def isMac(): Boolean = {
    try {
      val p =(Process("boot2docker version") !!)
      true
    } catch {
      case _: Throwable => false
    }
  }
}
