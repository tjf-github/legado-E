package io.legado.app.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 串行投递队列的 JVM 契约测试：书源调试的 Web 端摘要行与终止信号必须按提交顺序发送，
 * 否则终止任务可能先 cancelDebug(true) + close() 把摘要吞掉（P1 第二轮验收的阻塞项）。
 */
class SerialTaskQueueTest {

    private fun scope() = CoroutineScope(Job() + Dispatchers.Default)

    @Test
    fun tasksRunInSubmissionOrder() {
        val scope = scope()
        try {
            val queue = SerialTaskQueue(scope)
            val executed = Collections.synchronizedList(ArrayList<Int>())
            val done = CountDownLatch(200)
            repeat(200) { index ->
                queue.submit {
                    executed.add(index)
                    done.countDown()
                }
            }
            assertTrue("串行队列没有在超时内跑完", done.await(30, TimeUnit.SECONDS))
            assertEquals((0 until 200).toList(), executed.toList())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun tasksNeverOverlapUnderConcurrentSubmission() {
        val scope = scope()
        try {
            val queue = SerialTaskQueue(scope)
            val running = AtomicInteger()
            val overlaps = AtomicInteger()
            val done = CountDownLatch(120)
            val submitters = (0 until 4).map {
                Thread {
                    repeat(30) {
                        queue.submit {
                            if (running.incrementAndGet() > 1) overlaps.incrementAndGet()
                            Thread.sleep(1)
                            running.decrementAndGet()
                            done.countDown()
                        }
                    }
                }
            }
            submitters.forEach { it.start() }
            submitters.forEach { it.join() }
            assertTrue("并发提交的任务没有在超时内跑完", done.await(30, TimeUnit.SECONDS))
            assertEquals("同一时刻只能有一个任务在执行", 0, overlaps.get())
            assertEquals(0, running.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failingTaskDoesNotStopLaterTasks() {
        val scope = scope()
        try {
            val queue = SerialTaskQueue(scope)
            val executed = Collections.synchronizedList(ArrayList<Int>())
            val done = CountDownLatch(1)
            queue.submit { executed.add(0) }
            queue.submit { throw IllegalStateException("synthetic task failure") }
            queue.submit {
                executed.add(2)
                done.countDown()
            }
            assertTrue("失败任务后面的任务没有执行", done.await(30, TimeUnit.SECONDS))
            assertEquals(listOf(0, 2), executed.toList())
        } finally {
            scope.cancel()
        }
    }
}
