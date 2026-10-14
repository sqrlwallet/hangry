package com.kevan.hangry.perf

import java.lang.management.ManagementFactory

/**
 * Tiny harness for the JVM performance benchmarks in this package. They only run with
 * HANGRY_BENCH=1 in the environment, so the normal test run stays fast:
 *   HANGRY_BENCH=1 ./gradlew :app:testDebugUnitTest --tests 'com.kevan.hangry.perf.*' -i
 */
internal object PerfBench {
    val enabled: Boolean get() = System.getenv("HANGRY_BENCH") == "1"

    private val threads = ManagementFactory.getThreadMXBean()

    /** CPU time used so far by [thread], in milliseconds. */
    fun cpuMs(thread: Thread): Double = threads.getThreadCpuTime(thread.id) / 1e6

    fun report(name: String, samples: List<Double>) {
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val p90 = sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.lastIndex)]
        println("BENCH %-48s median %8.3f ms  p90 %8.3f ms  (n=%d)".format(name, median, p90, samples.size))
    }

    /** Runs [block] [warmup] times untimed, then [runs] times, reporting wall time. */
    inline fun measure(name: String, warmup: Int = 20, runs: Int = 50, block: () -> Unit) {
        repeat(warmup) { block() }
        val samples = List(runs) {
            val t = System.nanoTime()
            block()
            (System.nanoTime() - t) / 1e6
        }
        report(name, samples)
    }
}
