/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.utils.FileCache;
import com.android.utils.FileUtils;
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Suppliers;
import java.io.File;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * A scope containing data for the Android plugin.
 */
public class GlobalScope extends TaskOutputHolderImpl
        implements TransformGlobalScope, TaskOutputHolder {

    @NonNull private final Project project;
    @NonNull private final AndroidBuilder androidBuilder;
    @NonNull private final AndroidConfig extension;
    @NonNull private final SdkHandler sdkHandler;
    @NonNull private final NdkHandler ndkHandler;
    @NonNull private final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull private final Set<OptionalCompilationStep> optionalCompilationSteps;
    @NonNull private final ProjectOptions projectOptions;
    @NonNull private final Supplier<FileCache> projectLevelCache;
    @Nullable private final FileCache buildCache;

    // TODO: Remove mutable state from this class.
    @Nullable private File mockableAndroidJarFile;

    @Nullable private ConfigurableFileCollection java8LangSupportJar = null;

    public GlobalScope(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @Nullable FileCache buildCache,
            @NonNull Supplier<FileCache> projectLevelCache) {
        // Attention: remember that this code runs early in the build lifecycle, project may not
        // have been fully configured yet (e.g. buildDir can still change).
        this.project = checkNotNull(project);
        this.androidBuilder = checkNotNull(androidBuilder);
        this.extension = checkNotNull(extension);
        this.sdkHandler = checkNotNull(sdkHandler);
        this.ndkHandler = checkNotNull(ndkHandler);
        this.toolingRegistry = checkNotNull(toolingRegistry);
        this.optionalCompilationSteps = checkNotNull(projectOptions.getOptionalCompilationSteps());
        this.projectOptions = checkNotNull(projectOptions);
        this.buildCache = buildCache;

        // Guava provides thread-safe memoization, but we need to wrap it in java.util.Supplier.
        // This needs to be lazy, because rootProject.buildDir may be changed after the plugin is applied.
        this.projectLevelCache = Suppliers.memoize(projectLevelCache::get)::get;
    }

    @NonNull
    @Override
    public Project getProject() {
        return project;
    }

    @NonNull
    public AndroidConfig getExtension() {
        return extension;
    }

    @NonNull
    public AndroidBuilder getAndroidBuilder() {
        return androidBuilder;
    }

    @NonNull
    public String getProjectBaseName() {
        return (String) project.property("archivesBaseName");
    }

    @NonNull
    public SdkHandler getSdkHandler() {
        return sdkHandler;
    }

    @NonNull
    public NdkHandler getNdkHandler() {
        return ndkHandler;
    }

    @NonNull
    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
    }

    @NonNull
    @Override
    public File getBuildDir() {
        return project.getBuildDir();
    }

    @NonNull
    public File getIntermediatesDir() {
        return new File(getBuildDir(), FD_INTERMEDIATES);
    }

    @NonNull
    public File getGeneratedDir() {
        return new File(getBuildDir(), FD_GENERATED);
    }

    @NonNull
    public File getReportsDir() {
        return new File(getBuildDir(), FD_REPORTS);
    }

    public File getTestResultsFolder() {
        return new File(getBuildDir(), "test-results");
    }

    public File getTestReportFolder() {
        return new File(getBuildDir(), "reports/tests");
    }

    @NonNull
    public File getMockableAndroidJarFile() {
        if (mockableAndroidJarFile == null) {
            // Since the file ends up in $rootProject.buildDir, it will survive clean
            // operations - projects generated by AS don't have a top-level clean task that
            // would delete the top-level build directory. This means that the name has to
            // encode all the necessary information, otherwise the task will be UP-TO-DATE
            // even if the file should be regenerated. That's why we put the SDK version and
            // "default-values" in there, so if one project uses the returnDefaultValues flag,
            // it will just generate a new file and not change the semantics for other
            // sub-projects.

            // Figure out the "base name", by escaping any Windows-unfriendly characters from the
            // SDK version (e.g. 'Google Inc.:Google APIs')
            CharMatcher safeCharacters =
                    CharMatcher.JAVA_LETTER_OR_DIGIT.or(CharMatcher.anyOf("-."));
            String sdkName =
                    safeCharacters.negate().replaceFrom(
                            getExtension().getCompileSdkVersion(), '-');

            // Figure out the suffix, which encodes flags used to generate the jar.
            StringBuilder suffix = new StringBuilder();
            if (getExtension().getTestOptions().getUnitTests().isReturnDefaultValues()) {
                suffix.append(".default-values");
            }
            suffix.append(".v2"); // Does not contain Android resources or assets.
            suffix.append(".jar");

            File outDir =
                    new File(
                            getProject().getRootProject().getBuildDir(),
                            AndroidProject.FD_GENERATED);
            mockableAndroidJarFile = new File(outDir, "mockable-" + sdkName + suffix);
        }
        return mockableAndroidJarFile;
    }

    @NonNull
    public File getOutputsDir() {
        return new File(getBuildDir(), FD_OUTPUTS);
    }

    /**
     * Obtains the default location for APKs.
     *
     * @return the default location for APKs
     */
    @NonNull
    private File getDefaultApkLocation() {
        return FileUtils.join(getBuildDir(), FD_OUTPUTS, "apk");
    }

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    @NonNull
    public File getApkLocation() {
        return MoreObjects.firstNonNull(
                AndroidGradleOptions.getApkLocation(project),
                getDefaultApkLocation());
    }

    @Override
    public boolean isActive(OptionalCompilationStep step) {
        return optionalCompilationSteps.contains(step);
    }

    @NonNull
    public String getArchivesBaseName() {
        return (String)getProject().getProperties().get("archivesBaseName");
    }

    @NonNull
    public File getJacocoAgentOutputDirectory() {
        return new File(getIntermediatesDir(), "jacoco");
    }

    @NonNull
    public File getJacocoAgent() {
        return new File(getJacocoAgentOutputDirectory(), "jacocoagent.jar");
    }

    @NonNull
    @Override
    public ProjectOptions getProjectOptions() {
        return projectOptions;
    }

    @Nullable
    @Override
    public FileCache getBuildCache() {
        return buildCache;
    }

    @NonNull
    public FileCache getProjectLevelCache() {
        return projectLevelCache.get();
    }

    @NonNull
    public ConfigurableFileCollection getJava8LangSupportJar() {
        if (java8LangSupportJar == null) {
            java8LangSupportJar =
                    getProject()
                            .files(
                                    FileUtils.join(
                                            getIntermediatesDir(),
                                            "processing-tools",
                                            "java8-lang-support",
                                            "desugar.jar"));
        }
        return java8LangSupportJar;
    }
}
