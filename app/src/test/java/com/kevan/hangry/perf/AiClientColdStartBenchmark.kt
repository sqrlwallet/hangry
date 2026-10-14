package com.kevan.hangry.perf

import com.kevan.hangry.data.ai.OpenRouterClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * What creating the AI client costs the first time in a process - which today happens on the
 * main thread at launch, because the Posture, Nutrition and Ask Dash ViewModels are built then.
 * Run on its own (a fresh JVM), or other tests will already have loaded OkHttp.
 */
class AiClientColdStartBenchmark {
    @Test
    fun firstClientConstruction() {
        assumeTrue(PerfBench.enabled)
        val t = System.nanoTime()
        OpenRouterClient()
        PerfBench.report("OpenRouterClient(): first construction (cold)", listOf((System.nanoTime() - t) / 1e6))
    }
}
