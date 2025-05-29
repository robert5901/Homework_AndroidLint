package ru.otus.homework.lintchecks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class GlobalScopeUsageDetectorTest {

    private val lintTask = TestLintTask.lint()
        .allowMissingSdk()
        .issues(GlobalScopeUsageDetector.ISSUE)

    private val viewModelStub = java(
        """
    package androidx.lifecycle;
    
    public abstract class ViewModel {}    
    """.trimIndent()
    )

    private val viewModelExtensionsStub = kotlin(
        """
    package androidx.lifecycle
    import kotlinx.coroutines.CoroutineScope

    public val ViewModel.viewModelScope: CoroutineScope    
    """.trimIndent()
    )

    private val coroutineStub = kotlin(
        """
    package kotlinx.coroutines
    
    object GlobalScope: CoroutineScope

    public interface CoroutineScope

    fun CoroutineScope.launch(block: suspend () -> Unit) {}
    fun CoroutineScope.async(block: suspend () -> Unit) {}

    suspend fun delay(timeMillis: Long) {}
    """.trimIndent()
    )

    private val channelsStub = kotlin(
        """
    package kotlinx.coroutines.channels
    import kotlinx.coroutines.*
    
    public fun <E> CoroutineScope.actor(
        block: suspend () -> Unit
    ) {}
    """.trimIndent()
    )

    @Test
    fun `should detect GlobalScope usage case1`() {

        val testFile = TestFiles.kotlin(
            """
            package test.pkg
            
            import androidx.lifecycle.ViewModel
            import kotlinx.coroutines.GlobalScope
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.launch
            import kotlinx.coroutines.channels.actor
            
            class GlobalScopeTestCase : ViewModel() {
            
                fun case1() {
                    GlobalScope.launch {
                        delay(1000)
                        println("Hello World")
                    }
                    GlobalScope.actor<String> {
                        delay(1000)
                        println("Hello World")
                    }
                }
            }
            """.trimIndent()
        ).indented()

        lintTask
            .files(viewModelStub, channelsStub, coroutineStub, testFile)
            .run()
            .expectWarningCount(2)
            .expect(
                """
                src/test/pkg/GlobalScopeTestCase.kt:12: Warning: Замените GlobalScope на Scope контролируемые жизненным циклом класса [GlobalScopeUsage]
                        GlobalScope.launch {
                        ^
                src/test/pkg/GlobalScopeTestCase.kt:16: Warning: Замените GlobalScope на Scope контролируемые жизненным циклом класса [GlobalScopeUsage]
                        GlobalScope.actor<String> {
                        ^
                0 errors, 2 warnings
            """.trimIndent()
            )
    }

    @Test
    fun `should detect GlobalScope usage case2`() {

        val testFile = TestFiles.kotlin(
            """
            package test.pkg
            
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.GlobalScope
            import kotlinx.coroutines.async
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.launch
            
            class GlobalScopeTestCase(private val scope: CoroutineScope) : ViewModel() {
            
                fun case2() {
                    viewModelScope.launch {
                        val deferred = GlobalScope.async {
                            delay(1000)
                            "Hello World"
                        }
                        println(deferred.await())
                    }
                }
            }
            """.trimIndent()
        ).indented()

        lintTask
            .files(viewModelStub, viewModelExtensionsStub, coroutineStub, testFile)
            .run()
            .expectWarningCount(1)
            .expect(
                """
                    src/test/pkg/GlobalScopeTestCase.kt:15: Warning: Замените GlobalScope на Scope контролируемые жизненным циклом класса [GlobalScopeUsage]
                                val deferred = GlobalScope.async {
                                               ^
                    0 errors, 1 warnings
                """.trimIndent()
            )
    }

    @Test
    fun `should not detect GlobalScope usage`() {

        val testFile = TestFiles.kotlin(
            """
            package test.pkg

            import androidx.lifecycle.ViewModel
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.delay
            import kotlinx.coroutines.launch
            
            class GlobalScopeTestCase(private val scope: CoroutineScope) : ViewModel() {
            
                fun case3() {
                    scope.launch {
                        delay(1000)
                        println("Hello World")
                    }
                }
            }
            """.trimIndent()
        ).indented()

        lintTask
            .files(viewModelStub, coroutineStub, testFile)
            .run()
            .expectWarningCount(0)
            .expect(
                """No warnings.""".trimIndent()
            )
    }
}