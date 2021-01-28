/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.jdk

import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.Description
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.notification.RunNotifier
import java.io.File

annotation class RunOnlyJdk6Test

class JUnitPlatformRunnerForJdk6(testClass: Class<*>) : JUnitPlatform(testClass) {
    init {
        performFilter(testClass, this) f@{ description ->
            @Suppress("NAME_SHADOWING")
            val testClass = description.testClass ?: return@f null
            val methodName = description.methodName ?: return@f null

            val testClassAnnotation = testClass.getAnnotation(TestMetadata::class.java) ?: return@f null
            val method = testClass.getMethod(methodName)
            val methodAnnotation = method.getAnnotation(TestMetadata::class.java) ?: return@f null
            "${testClassAnnotation.value}/${methodAnnotation.value}"
        }
    }

    override fun run(notifier: RunNotifier?) {
        SeparateJavaProcessHelper.setUp()
        try {
            super.run(notifier)
        } finally {
            SeparateJavaProcessHelper.tearDown()
        }
    }
}

fun performFilter(
    klass: Class<*>,
    runner: Filterable,
    testDataPathExtractor: (Description) -> String?,
) {
    if (klass.getAnnotation(RunOnlyJdk6Test::class.java) != null) {
        runner.filter(object : Filter() {
            override fun shouldRun(description: Description): Boolean {
                if (description.isTest) {
                    val path = testDataPathExtractor(description) ?: return true
                    val fileText = File(path).readText()
                    return !InTextDirectivesUtils.isDirectiveDefined(fileText, "// JVM_TARGET:") &&
                            !InTextDirectivesUtils.isDirectiveDefined(fileText, "// SKIP_JDK6")
                }
                return true
            }

            override fun describe(): String {
                return "skipped on JDK 6"
            }
        })
    }

}
