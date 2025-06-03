package ru.otus.homework.lintchecks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParentOfType

private const val ID = "JobInBuilderUsage"

private const val BRIEF_DESCRIPTION =
    "Не используйте Job/SupervisorJob для передачи в корутин билдер."
private const val EXPLANATION = "Не используйте Job/SupervisorJob для передачи в корутин билдер. " +
        "Хоть Job и его наследники являются элементами CoroutineContext, их использование внутри корутин-билдеров не имеет никакого эффекта, " +
        "это может сломать ожидаемые обработку ошибок и механизм отмены корутин."

private const val PRIORITY = 6

private const val JOB_CLASS = "kotlinx.coroutines.Job"
private const val COMPLETABLE_JOB_CLASS = "kotlinx.coroutines.CompletableJob"
private const val NON_CANCELABLE_CLASS = "kotlinx.coroutines.NonCancellable"

private const val SUPERVISOR_JOB_FIX_NAME = "Удалить SupervisorJob"
private const val SUPERVISOR_JOB_CALL_NAME = "SupervisorJob"

private const val VIEW_MODEL_CLASS = "androidx.lifecycle.ViewModel"
private const val VIEW_MODEL_SCOPE_TEXT = "viewModelScope"

const val regexWithOperand = """\s*\+\s*"""
const val regexParenthesized = """\(\s*SupervisorJob\s*\(\s*\)\s*\)"""
const val regexDef = """SupervisorJob\s*\(\s*\)"""

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
            if (node.methodIdentifier?.name in listOf("launch", "async")) {
                val argument = node.valueArguments.getOrNull(0)
                checkArgument(argument, node)
            }
        }

        private fun checkArgument(
            argument: UExpression?,
            node: UCallExpression,
            isRightOperand: Boolean = false,
            isLeftOperand: Boolean = false,
            isParenthesized: Boolean = false
        ) {
            when (argument) {
                is UParenthesizedExpression -> {
                    checkArgument(
                        argument.expression,
                        node,
                        isRightOperand = isRightOperand,
                        isLeftOperand = isLeftOperand,
                        isParenthesized = true
                    )
                }

                is UBinaryExpression -> {
                    checkArgument(argument.leftOperand, node, isLeftOperand = true)
                    checkArgument(argument.rightOperand, node, isRightOperand = true)
                }

                is UCallExpression -> {
                    if (argument.getExpressionType()?.canonicalText == JOB_CLASS || argument.getExpressionType()?.superTypes?.any { it.canonicalText == JOB_CLASS } == true) {
                        val lintFix = if (
                            argument.classReference?.getExpressionType()?.canonicalText == COMPLETABLE_JOB_CLASS
                            && argument.methodName == SUPERVISOR_JOB_CALL_NAME
                            && isOnViewModelScope(node)
                        ) {
                            createSupervisorJobFix(
                                isRightOperand = isRightOperand,
                                isLeftOperand = isLeftOperand,
                                isParenthesized = isParenthesized
                            )
                        } else null

                        makeReport(context.getLocation(node.valueArguments[0]), lintFix)
                    }
                }

                is UReferenceExpression -> {
                    if (argument.getExpressionType()?.canonicalText == JOB_CLASS || argument.getExpressionType()?.superTypes?.any { it.canonicalText == JOB_CLASS } == true) {
                        if (
                            argument.getExpressionType()?.canonicalText == NON_CANCELABLE_CLASS
                            && node.receiver == null
                        ) {
                            makeReport(context.getLocation(node), createNonCancelableFix(node))
                        } else {
                            makeReport(context.getLocation(argument))
                        }
                    }
                }
            }
        }

        private fun isOnViewModelScope(node: UCallExpression): Boolean {
            return context.evaluator.extendsClass(node.getParentOfType<UClass>()?.javaPsi, VIEW_MODEL_CLASS)
                    && node.receiver is USimpleNameReferenceExpression
                    && (node.receiver as USimpleNameReferenceExpression).sourcePsi?.text == VIEW_MODEL_SCOPE_TEXT
        }

        private fun createSupervisorJobFix(
            isRightOperand: Boolean = false,
            isLeftOperand: Boolean = false,
            isParenthesized: Boolean = false
        ): LintFix {
            var replaceText = if (isParenthesized) {
                regexParenthesized
            } else {
                regexDef
            }

            if (isRightOperand) {
                replaceText = regexWithOperand + replaceText
            }
            if (isLeftOperand) {
                replaceText += regexWithOperand
            }

            return LintFix.create()
                .name(SUPERVISOR_JOB_FIX_NAME)
                .replace()
                .pattern(replaceText)
                .with("")
                .build()
        }

        private fun makeReport(location: Location, lintFix: LintFix? = null) {
            context.report(
                ISSUE,
                location,
                BRIEF_DESCRIPTION,
                lintFix
            )
        }

        private fun createNonCancelableFix(node: UCallExpression): LintFix {
            val coroutineBuilderName = node.methodIdentifier?.name?: ""
            return LintFix.create()
                .name("Заменить $coroutineBuilderName на withContext")
                .replace()
                .text(coroutineBuilderName)
                .with("withContext")
                .build()
        }
    }
}