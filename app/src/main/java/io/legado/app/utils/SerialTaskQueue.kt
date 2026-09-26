package io.legado.app.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * 按提交顺序串行执行任务的队列。
 *
 * 书源调试的 Web 端日志必须保序：四行诊断摘要与随后的终止信号如果各自成为一个并发 IO 任务，
 * 终止任务可能先关掉 socket（见 BookSourceDebugWebSocket.printLog），摘要就丢了。
 * 这里用无界通道 + 单个消费者，把「入队顺序」变成「实际发送顺序」：任意线程提交，
 * 同一时刻只有一个任务在执行。
 *
 * 队列不额外持有线程：消费者随 [scope] 结束而取消，未执行的任务被丢弃。
 */
class SerialTaskQueue(
    private val scope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.IO
) {

    private val tasks = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(context) {
            for (task in tasks) {
                try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // 单个任务失败不得中断后面的任务；失败由任务自己上报（调用方负责 runCatching）
                }
            }
        }
    }

    /** 提交任务：提交顺序即执行顺序。 */
    fun submit(task: suspend () -> Unit) {
        tasks.trySend(task)
    }
}
