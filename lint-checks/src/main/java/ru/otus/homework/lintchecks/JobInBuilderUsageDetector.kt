package ru.otus.homework.lintchecks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression

private const val ID = "JobInBuilderUsage"

private const val BRIEF_DESCRIPTION =
    "Не используйте Job/SupervisorJob для передачи в корутин билдер."
private const val EXPLANATION = "Не используйте Job/SupervisorJob для передачи в корутин билдер. " +
        "Хоть Job и его наследники являются элементами CoroutineContext, их использование внутри корутин-билдеров не имеет никакого эффекта, " +
        "это может сломать ожидаемые обработку ошибок и механизм отмены корутин."

private const val PRIORITY = 6

private const val COMPLETABLE_JOB_CLASS = "kotlinx.coroutines.CompletableJob"
private const val NON_CANCELABLE_CLASS = "kotlinx.coroutines.NonCancellable"
private const val JOB_CLASS = "kotlinx.coroutines.Job"

class JobInBuilderUsageDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            ID,
            BRIEF_DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            PRIORITY,
            Severity.WARNING,
            Implementation(JobInBuilderUsageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return MethodCallHandler(context)
    }

    class MethodCallHandler(private val context: JavaContext) : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName in listOf("launch", "async")) {
                val argument = node.valueArguments.getOrNull(0)
                checkArgument(argument)
            }
        }

        private fun checkArgument(argument: UExpression?) {
            when (argument) {
                is UParenthesizedExpression -> checkArgument(argument.expression)
                is UBinaryExpression -> {
                    checkArgument(argument.leftOperand)
                    checkArgument(argument.rightOperand)
                }

                is UCallExpression -> {
                    if (argument.getExpressionType()?.canonicalText == JOB_CLASS || argument.getExpressionType()?.superTypes?.any { it.canonicalText == JOB_CLASS } == true) {
                        makeReport(context.getLocation(argument))
                    }
                }

                is UReferenceExpression -> {
                    if (argument.getExpressionType()?.canonicalText == JOB_CLASS || argument.getExpressionType()?.superTypes?.any { it.canonicalText == JOB_CLASS } == true) {
                        makeReport(context.getLocation(argument))
                    }
                }
            }
        }

        private fun makeReport(location: Location) {
            context.report(
                ISSUE,
                location,
                BRIEF_DESCRIPTION
            )
        }
    }
}