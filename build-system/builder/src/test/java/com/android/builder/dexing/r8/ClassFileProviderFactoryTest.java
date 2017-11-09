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

package com.android.builder.dexing.r8;

import com.android.builder.dexing.DexArchiveTestUtil;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClassFileProviderFactoryTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        ClassFileProviderFactory factory1 = new ClassFileProviderFactory();
        ClassFileProviderFactory factory2 = new ClassFileProviderFactory();

        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(boas)) {
            oos.writeObject(factory1);
            oos.writeObject(factory2);
        }

        ClassFileProviderFactory factory1Bis;
        ClassFileProviderFactory factory2Bis;
        try (ObjectInputStream ois =
                new ObjectInputStream(new ByteArrayInputStream(boas.toByteArray()))) {
            factory1Bis = (ClassFileProviderFactory) ois.readObject();
            factory2Bis = (ClassFileProviderFactory) ois.readObject();
        }

        Assert.assertSame(factory1, factory1Bis);
        Assert.assertSame(factory2, factory2Bis);
        Assert.assertNotSame(factory1Bis, factory2Bis);
    }

    @Test
    public void testResourceCaching() throws Exception {
        int nbJars = 20;
        Path[] classpathEntries = new Path[nbJars];
        Collection<String> classes = Arrays.asList("A", "B", "C");
        List<String> descriptors = DexArchiveTestUtil.getTestClassesDescriptors(classes);
        for (int i = 0; i < nbJars; i++) {
            classpathEntries[i] = temporaryFolder.getRoot().toPath().resolve("input" + i + ".jar");
            DexArchiveTestUtil.createClasses(classpathEntries[i], classes);
        }
        Assert.assertEquals(classes.size(), descriptors.size());

        final ClassFileProviderFactory factory = new ClassFileProviderFactory();
        class Runner extends Thread {
            ClassFileResourceProvider[] providers = new ClassFileResourceProvider[nbJars];
            Resource[][] resources = new Resource[nbJars][classes.size()];
            ClassFileProviderFactory.Handler handler = factory.open();

            @Override
            public void run() {
                try {
                    for (int i = 0; i < nbJars; i++) {
                        // Note: keep the provider to prevent GC. If we don't GC, may collect the
                        // factory and no unicity of resource can be asserted.
                        providers[i] = handler.getProvider(classpathEntries[i]);
                        for (int j = 0; j < descriptors.size(); j++) {
                            resources[i][j] = providers[i].getResource(descriptors.get(j));
                        }
                    }
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        }
        Runner[] runners = new Runner[20];
        for (int i = 0; i < runners.length; i++) {
            runners[i] = new Runner();
        }
        for (Runner runner : runners) {
            runner.start();
        }
        for (Runner runner : runners) {
            runner.join();
        }
        for (Runner runner : runners) {
            // Close only when all threads have completed
            runner.handler.close();
        }

        Assert.assertEquals("Factory should be closed", 0, factory.providers.size());

        for (int i = 1; i < runners.length; i++) {

            for (int j = 0; j < nbJars; j++) {
                Assert.assertSame(runners[0].providers[j], runners[i].providers[j]);
                for (int k = 0; k < descriptors.size(); k++) {
                    Assert.assertSame(runners[0].resources[j][k], runners[i].resources[j][k]);
                }
            }
        }
    }
}
