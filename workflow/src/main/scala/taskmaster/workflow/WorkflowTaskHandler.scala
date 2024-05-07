package taskmaster.workflow

import fabric.rw.RW
import taskmaster.task.TaskHandler

trait WorkflowTaskHandler[Payload <: WorkflowPayload, Result] extends TaskHandler[Payload] {
  val resultRW: RW[Result]
}
