package taskmaster.workflow.db

import fabric.rw._

case class WorkflowUpdate(workflow: Workflow, scheduled: List[TaskInstance])

object WorkflowUpdate {
  implicit val rw: RW[WorkflowUpdate] = RW.gen
}