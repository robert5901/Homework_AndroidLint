package ru.otus.homework.lintchecks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.isKotlin
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getParentOfType

private const val ID = "GlobalScopeUsage"
private const val BRIEF_DESCRIPTION =
    "Замените GlobalScope на Scope контролируемые жизненным циклом класса"
private const val EXPLANATION =
    "Замените GlobalScope на Scope контролируемые жизненным циклом класса. " +
            "Корутины, запущенные на kotlinx.coroutines.GlobalScope нужно контролировать вне скоупа класс, в котором они созданы. " +
            "Контролировать глобальные корутины неудобно, а отсутствие контроля может привести к излишнему использованию ресурсов и утечкам памяти."
private const val PRIORITY = 6
private const val GLOBAL_SCOPE_CLASS = "kotlinx.coroutines.GlobalScope"

private const val VIEW_MODEL_CLASS = "androidx.lifecycle.ViewModel"
private const val VIEW_MODEL_ARTIFACT = "androidx.lifecycle:lifecycle-viewmodel-ktx"

private const val GLOBAL_SCOPE_TEXT = "GlobalScope"
private const val VIEW_MODEL_SCOPE_TEXT = "viewModelScope"

class GlobalScopeUsageDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            ID,
            BRIEF_DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            PRIORITY,
            Severity.WARNING,
            Implementation(GlobalScopeUsageDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return MethodCallHandler(context)
    }
}

class MethodCallHandler(private val context: JavaContext) : UElementHandler() {
    override fun visitCallExpression(node: UCallExpression) {
        val receiverType = node.receiverType?.canonicalText

        if (receiverType == GLOBAL_SCOPE_CLASS) {
            val isKotlin = isKotlin(node.sourcePsi)

            if (isKotlin
                && context.evaluator.extendsClass(
                    node.getParentOfType<UClass>()?.javaPsi,
                    VIEW_MODEL_CLASS
                )
                && context.evaluator.dependencies?.packageDependencies?.roots?.find {
                    it.artifactName == VIEW_MODEL_ARTIFACT
                } != null
            ) {
                context.report(
                    GlobalScopeUsageDetector.ISSUE,
                    context.getLocation(node),
                    BRIEF_DESCRIPTION,
                    createViewModelFix()
                )
            } else {
                context.report(
                    GlobalScopeUsageDetector.ISSUE,
                    context.getLocation(node),
                    BRIEF_DESCRIPTION
                )
            }
        }
    }

    private fun createViewModelFix(): LintFix {
        return LintFix.create()
            .replace()
            .text(GLOBAL_SCOPE_TEXT)
            .with(VIEW_MODEL_SCOPE_TEXT)
            .build()
    }
}