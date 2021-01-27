/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import java.io.Serializable


sealed class KotlinImportingDiagnostic : Serializable

typealias KotlinImportingDiagnosticsContainer = MutableSet<KotlinImportingDiagnostic>

data class OrphanSourceSetsImportingDiagnostic(val projectName: String, val sourceSetName: String) : KotlinImportingDiagnostic()