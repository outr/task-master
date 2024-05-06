package taskmaster.task

/**
 * TaskInstanceCreator is primarily used internally as a simplifier for building workflows
 */
case class TaskInstanceCreator[Payload](payload: Payload,
                                        handler: TaskHandler[Payload],
                                        dependencies: Set[TaskInstanceCreator[_]],
                                        taskId: TaskId)