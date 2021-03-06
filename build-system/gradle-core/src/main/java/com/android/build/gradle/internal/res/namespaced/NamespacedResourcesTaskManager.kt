/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.aapt.AaptGeneration
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.TaskOutputHolder
import com.android.build.gradle.internal.scope.TaskOutputHolder.TaskOutputType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.core.VariantType
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import org.gradle.api.Task
import java.io.File

/**
 * Responsible for the creation of tasks to build namespaced resources.
 */
class NamespacedResourcesTaskManager(
        private val globalScope: GlobalScope,
        private val taskFactory: TaskFactory,
        private val variantScope: VariantScope) {

    /**
     * Creates the tasks for dealing with resources in a namespaced way.
     *
     * The current implementation:
     *
     *  1. Links the app as a static library. This provides a *non-final* R-class, and means that
     *  the distinction between app and library is reduced. We can revisit that in the future if
     *  final ids in apps are a vital feature.
     *  2. Links the app and its dependency to produce the final APK. This re-uses the same
     *  [LinkApplicationAndroidResourcesTask] task, as it needs to be split aware.
     *
     * TODO: Test support, Synthesize non-namespaced output.
     */
    fun createNamespacedResourceTasks(
            resPackageOutputFolder: File,
            packageOutputType: TaskOutputType?,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val aaptGeneration = AaptGeneration.fromProjectOptions(globalScope.projectOptions)
        Preconditions.checkState(aaptGeneration == AaptGeneration.AAPT_V2_DAEMON_MODE,
                "Resource Namespacing can only be used with aapt2")
        // Compile
        createCompileResourcesTask()
        createLinkResourcesTask()
        createNamespacedLibraryRFiles()

        if (variantScope.variantData.type == VariantType.LIBRARY || variantScope.testedVariantData?.type == VariantType.LIBRARY) {
            createNamespacedLibraryProcessResourcesTask(
                    resPackageOutputFolder = resPackageOutputFolder,
                    packageOutputType = packageOutputType)
        } else {
            createNamespacedAppProcessTask(
                    resPackageOutputFolder = resPackageOutputFolder,
                    packageOutputType = packageOutputType,
                    baseName = baseName,
                    useAaptToGenerateLegacyMultidexMainDexProguardRules = useAaptToGenerateLegacyMultidexMainDexProguardRules)
        }
        createCompileRuntimeRClassTask()
    }

    private fun createNamespacedLibraryRFiles() {
        val rClassJarFile = File(
                variantScope.globalScope.intermediatesDir,
                "res-rJar/" + variantScope.variantConfiguration.dirName + "/R.jar")
        val resIdsFile = File(
                variantScope.globalScope.intermediatesDir,
                "res-ids/" + variantScope.variantConfiguration.dirName + "res-ids.txt")

        val task = taskFactory.create(
                GenerateNamespacedLibraryRFilesTask.ConfigAction(
                        variantScope,
                        variantScope.getOutput(TaskOutputType.SYMBOL_LIST),
                        rClassJarFile,
                        resIdsFile))

        variantScope.addTaskOutput(
                TaskOutputType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
                rClassJarFile,
                task.name)
        variantScope.addTaskOutput(
                TaskOutputType.NAMESPACED_SYMBOL_LIST_WITH_PACKAGE_NAME,
                resIdsFile,
                task.name)

    }

    private fun createCompileRuntimeRClassTask() {
        val rClassCompiledOutputDir = File(
                variantScope.globalScope.intermediatesDir,
                "res-final-r-classes/" + variantScope.variantConfiguration.dirName)
        val task = taskFactory.create(
                CompileRClassTask.ConfigAction(
                        variantScope.getTaskName("compile", "FinalRClass"),
                        variantScope.getOutput(TaskOutputType.RUNTIME_R_CLASS_SOURCES),
                        rClassCompiledOutputDir
                ))
        variantScope.addTaskOutput(
                TaskOutputType.RUNTIME_R_CLASS_CLASSES,
                rClassCompiledOutputDir,
                task.name)
    }

    private fun createNamespacedAppProcessTask(
            resPackageOutputFolder: File,
            packageOutputType: TaskOutputType?,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val runtimeRClassSources = File(globalScope.generatedDir,
                "source/final-r/" + variantScope.variantConfiguration.dirName)
        val process = taskFactory.create(
                LinkApplicationAndroidResourcesTask.NamespacedConfigAction(
                        variantScope,
                        runtimeRClassSources,
                        resPackageOutputFolder,
                        variantScope.variantData.type == VariantType.LIBRARY,
                        useAaptToGenerateLegacyMultidexMainDexProguardRules,
                        baseName))
        variantScope.addTaskOutput(
                TaskOutputType.PROCESSED_RES,
                resPackageOutputFolder,
                process.name)
        variantScope.addTaskOutput(
                TaskOutputType.RUNTIME_R_CLASS_SOURCES,
                runtimeRClassSources,
                process.name)
        if (packageOutputType != null) {
            variantScope.addTaskOutput(
                    packageOutputType,
                    variantScope.processResourcePackageOutputDirectory,
                    process.name)
        }
    }

    private fun createNamespacedLibraryProcessResourcesTask(
            resPackageOutputFolder: File,
            packageOutputType: TaskOutputType?) {
        val runtimeRClassSources = File(globalScope.generatedDir,
                "source/final-r/" + variantScope.variantConfiguration.dirName)
        val process = taskFactory.create(
                ProcessAndroidAppResourcesTask.ConfigAction(
                        variantScope,
                        runtimeRClassSources,
                        File(resPackageOutputFolder, "res.apk"),
                        variantScope.variantData.type == VariantType.LIBRARY))
        variantScope.addTaskOutput(
                TaskOutputType.PROCESSED_RES,
                resPackageOutputFolder,
                process.name)
        variantScope.addTaskOutput(
                TaskOutputType.RUNTIME_R_CLASS_SOURCES,
                runtimeRClassSources,
                process.name)
        if (packageOutputType != null) {
            variantScope.addTaskOutput(
                    packageOutputType,
                    variantScope.processResourcePackageOutputDirectory,
                    process.name)
        }
    }

    private fun createCompileResourcesTask() {
        val compiledDirectory =
                FileUtils.join(
                        variantScope.globalScope.intermediatesDir,
                        SdkConstants.FD_RES,
                        SdkConstants.FD_COMPILED,
                        variantScope.variantConfiguration.dirName)


        val sourceSets = variantScope.variantData.variantConfiguration.sortedSourceProviders

        val tasks = mutableListOf<Task>()
        // Preserving the sourceset order in overlays is important.
        val directories = mutableListOf<File>()

        for (sourceSet in sourceSets) {
            // Just number the multiple resource directories in a source set.
            // e.g. if debug has two directories, they will be stored in the output directory as
            // 'debug' and 'debug2'
            for ((index, resDirectory) in sourceSet.resDirectories.withIndex()) {
                val sourceSetDirName =
                        if (index == 0) sourceSet.name else """${sourceSet.name}${index + 1}"""
                val outputDir = File(compiledDirectory, sourceSetDirName)
                val name = "compile${sourceSetDirName.capitalize()}" +
                        "ResourcesFor${variantScope.fullVariantName.capitalize()}"
                tasks.add(taskFactory.create(CompileSourceSetResources.ConfigAction(
                        name = name,
                        inputDirectory = resDirectory,
                        outputDirectory = outputDir,
                        variantScope = variantScope,
                        aaptIntermediateDirectory = variantScope.getIncrementalDir(name))))
                directories.add(outputDir)
            }
        }
        val compiled = variantScope.globalScope.project.files(directories)

        tasks.forEach {
            it.dependsOn(variantScope.resourceGenTask)
            compiled.builtBy(it)
        }
        variantScope.addTaskOutput(
                TaskOutputHolder.TaskOutputType.RES_COMPILED_FLAT_FILES, compiled, null)
    }

    private fun createLinkResourcesTask() {
        val rDotTxt = File(globalScope.intermediatesDir,
                "symbols/" + variantScope.variantConfiguration.dirName + "/" + FN_RESOURCE_TEXT)
        val resourceStaticLibrary =
                FileUtils.join(globalScope.intermediatesDir,
                        "res-linked",
                        variantScope.variantConfiguration.dirName,
                        "res.apk")
        // We only need this because of b/69956357. Remove generating R.java once bug is fixed.
        val compileOnlyRClassSourceDir = File(globalScope.generatedDir,
                "source/r/" + variantScope.variantConfiguration.dirName)
        val link = taskFactory.create(
                LinkLibraryAndroidResourcesTask.ConfigAction(
                        variantScope, compileOnlyRClassSourceDir, resourceStaticLibrary, rDotTxt))
        variantScope.addTaskOutput(
                TaskOutputType.RES_STATIC_LIBRARY,
                resourceStaticLibrary,
                link.name)
        variantScope.addTaskOutput(
                TaskOutputType.SYMBOL_LIST,
                rDotTxt,
                link.name)
    }
}