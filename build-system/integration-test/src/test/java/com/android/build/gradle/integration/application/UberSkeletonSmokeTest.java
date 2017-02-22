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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.builder.model.AndroidProject;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@Ignore("http://b.android.com/234781")
public class UberSkeletonSmokeTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromExternalProject("android-studio-gradle-test")
                    .withHeap("20G")
                    .create();

    @Before
    public void setUp() throws IOException {
        PerformanceTestProjects.initializeUberSkeleton(project);
    }

    @Test
    public void checkModel() throws Exception {
        GetAndroidModelAction.ModelContainer<AndroidProject> modelContainer =
                project.model().withArgument("--continue").ignoreSyncIssues().getMulti();
        Map<String, AndroidProject> models = modelContainer.getModelMap();

        PerformanceTestProjects.assertNoSyncErrors(models);
    }
}
