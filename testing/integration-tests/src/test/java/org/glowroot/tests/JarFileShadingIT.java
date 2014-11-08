/*
 * Copyright 2011-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.tests;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.common.collect.Lists;
import org.junit.Assume;
import org.junit.Test;

import org.glowroot.MainEntryPoint;
import org.glowroot.container.ClassPath;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class JarFileShadingIT {

    @Test
    public void shouldCheckThatJarIsWellShaded() throws IOException {
        File glowrootCoreJarFile = ClassPath.getGlowrootCoreJarFile();
        if (glowrootCoreJarFile == null) {
            if (System.getProperty("surefire.test.class.path") != null) {
                throw new IllegalStateException(
                        "Running inside maven and can't find glowroot-core.jar on class path");
            }
            // try to cover the non-standard case when running outside of maven (e.g. inside an IDE)
            glowrootCoreJarFile = getGlowrootCoreJarFileFromRelativePath();
            // don't worry if glowroot jar can't be found while running outside of maven
            Assume.assumeNotNull(glowrootCoreJarFile);
        }
        List<String> acceptableEntries = Lists.newArrayList();
        acceptableEntries.add("glowroot\\..*");
        acceptableEntries.add("org/");
        acceptableEntries.add("org/glowroot/.*");
        acceptableEntries.add("META-INF/");
        acceptableEntries.add("META-INF/maven/.*");
        acceptableEntries.add("META-INF/services/");
        acceptableEntries.add("META-INF/services/org.glowroot.shaded.xnio.XnioProvider");
        acceptableEntries.add("META-INF/MANIFEST\\.MF");
        acceptableEntries.add("META-INF/LICENSE");
        acceptableEntries.add("META-INF/NOTICE");
        JarFile jarFile = new JarFile(glowrootCoreJarFile);
        List<String> unacceptableEntries = Lists.newArrayList();
        for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
            JarEntry jarEntry = e.nextElement();
            if (!acceptableJarEntry(jarEntry, acceptableEntries)) {
                unacceptableEntries.add(jarEntry.getName());
            }
        }
        assertThat(unacceptableEntries).isEmpty();
        // cleanup
        jarFile.close();
    }

    // try to cover the non-standard case when running from inside an IDE
    private static File getGlowrootCoreJarFileFromRelativePath() {
        String classesDir = MainEntryPoint.class.getProtectionDomain().getCodeSource()
                .getLocation().getFile();
        // guessing this is target/classes
        File targetDir = new File(classesDir).getParentFile();
        File[] possibleMatches = targetDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("glowroot-core-[0-9.]+(-SNAPSHOT)?.jar");
            }
        });
        if (possibleMatches == null || possibleMatches.length == 0) {
            return null;
        } else if (possibleMatches.length == 1) {
            return possibleMatches[0];
        } else {
            throw new IllegalStateException("More than one possible match found for glowroot.jar");
        }
    }

    private static boolean acceptableJarEntry(JarEntry jarEntry, List<String> acceptableEntries) {
        for (String acceptableEntry : acceptableEntries) {
            if (jarEntry.getName().matches(acceptableEntry)) {
                return true;
            }
        }
        return false;
    }
}
