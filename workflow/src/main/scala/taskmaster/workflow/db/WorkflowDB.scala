package taskmaster.workflow.db

import cats.effect.IO
import lightdb.LightDB
import lightdb.halo.HaloDBSupport
import lightdb.model.Collection
import lightdb.upgrade.DatabaseUpgrade
import taskmaster.scheduler.db.SchedulerDB

import java.nio.file.Path

/**
 * WorkflowDB represents the structure for working with the database related to workflows
 */
object WorkflowDB extends LightDB with HaloDBSupport {
  override def directory: Path = Path.of("db", "workflow")

  override def collections: List[Collection[_]] = List(TaskRun, Workflow)

  override def upgrades: List[DatabaseUpgrade] = Nil

  override def init(truncate: Boolean): IO[Unit] = SchedulerDB.init(truncate).flatMap(_ => super.init(truncate))

  override def truncate(): IO[Unit] = SchedulerDB.truncate().flatMap(_ => super.truncate())

  override def dispose(): IO[Unit] = SchedulerDB.dispose().flatMap(_ => super.dispose())
}