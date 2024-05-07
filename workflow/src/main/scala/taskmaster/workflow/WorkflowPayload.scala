package taskmaster.workflow

import lightdb.Id
import taskmaster.workflow.db.Workflow

/**
 * Base trait for Workflow payloads to make sure there's a workflowId
 */
trait WorkflowPayload {
  def workflowId: Id[Workflow]
}