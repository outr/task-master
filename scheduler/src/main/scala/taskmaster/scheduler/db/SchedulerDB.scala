package taskmaster.scheduler.db

import lightdb.LightDB
import lightdb.halo.HaloDBSupport
import lightdb.model.Collection
import lightdb.upgrade.DatabaseUpgrade

import java.nio.file.Path

object SchedulerDB extends LightDB with HaloDBSupport {
  override def directory: Path = Path.of("db/scheduler")

  override def collections: List[Collection[_]] = List(ScheduledEvent)

  override def upgrades: List[DatabaseUpgrade] = Nil
}
