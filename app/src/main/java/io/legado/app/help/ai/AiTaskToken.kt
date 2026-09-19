package io.legado.app.help.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** One current chapter/configuration lifetime. Stage D must invalidate it on navigation/disable.
 * Invalidating and committing use the same monitor, so an invalidated token cannot publish. */
class AiTaskToken {
    private var valid = true
    private val jobs = mutableSetOf<Job>()

    @Synchronized fun invalidate() {
        valid = false
        jobs.toList().forEach { it.cancel(CancellationException("ai_task_invalidated")) }
    }
    @Synchronized internal fun attach(job: Job) { checkCurrent(); jobs.add(job) }
    @Synchronized internal fun detach(job: Job) { jobs.remove(job) }
    @Synchronized internal fun checkCurrent() {
        if (!valid) throw CancellationException("ai_task_invalidated")
    }
    @Synchronized internal fun <T> withCurrent(block: () -> T): T { checkCurrent(); return block() }
}
