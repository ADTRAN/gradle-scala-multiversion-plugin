/*
 * Copyright 2017 ADTRAN, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package integration

import common.SimpleProjectTest
import org.gradle.testkit.runner.GradleRunner
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class TestScalaMultiVersionPlugin extends GroovyTestCase implements SimpleProjectTest {
    void testOneTaskOneVersion() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1", "-PscalaVersions=1.1.1")
            .build()
        assert result.tasks.size() == 1
        assert result.taskPaths(SUCCESS).containsAll(":t1")
        assert result.output.contains("t1 1.1.1")
    }

    void testMultipleTasksOneVersion() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1", "t2", "-PscalaVersions=1.1.1")
            .build()
        assert result.taskPaths(SUCCESS).containsAll(":t1", ":t2")
        assert result.output.contains("t1 1.1.1")
        assert result.output.contains("t2 1.1.1")
    }

    void testOneTaskMultipleVersions() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1", "-PscalaVersions=1.1.1,2.2.2,3.3.3")
            .build()
        assert result.taskPaths(SUCCESS).toSorted() == [":t1",
                                                        ":recurseWithScalaVersion_2.2.2",
                                                        ":codeProject:t1",
                                                        ":recurseWithScalaVersion_3.3.3",
                                                        ":codeProject:t1"].sort()
        assert result.output.contains("t1 1.1.1")
        assert result.output.contains("t1 2.2.2")
        assert result.output.contains("t1 3.3.3")
    }

    void testMultipleTasksMultipleVersions() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1", "t2", "-PscalaVersions=1.1.1,2.2.2")
            .build()
        assert result.taskPaths(SUCCESS).toSorted() == [":t1", ":t2",
                                                        ":recurseWithScalaVersion_2.2.2",
                                                        ":codeProject:t1", ":codeProject:t2"].sort()
        assert result.output.contains("t1 1.1.1")
        assert result.output.contains("t1 2.2.2")
        assert result.output.contains("t2 1.1.1")
        assert result.output.contains("t2 2.2.2")
    }

    void testDefaultVersion() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1", "-PscalaVersions=1.1.1,2.2.2,3.3.3", "-PdefaultScalaVersions=2.2.2,3.3.3")
            .build()
        assert result.taskPaths(SUCCESS).toSorted() == [":t1",
                                                        ":recurseWithScalaVersion_3.3.3",
                                                        ":codeProject:t1"].sort()
        assert result.taskPaths(SUCCESS).containsAll(":t1", ":recurseWithScalaVersion_3.3.3")
        assert !result.output.contains("t1 1.1.1")
        assert result.output.contains("t1 2.2.2")
        assert result.output.contains("t1 3.3.3")
    }

    void testAllScalaVersions() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1",
                           "-PscalaVersions=1.1.1,2.2.2,3.3.3",
                           "-PdefaultScalaVersions=1.1.1",
                           "-PallScalaVersions")
            .build()
        assert result.taskPaths(SUCCESS).toSorted() == [":t1",
                                                        ":recurseWithScalaVersion_2.2.2",
                                                        ":codeProject:t1",
                                                        ":recurseWithScalaVersion_3.3.3",
                                                        ":codeProject:t1"].sort()
        assert result.output.contains("t1 1.1.1")
        assert result.output.contains("t1 2.2.2")
        assert result.output.contains("t1 3.3.3")
    }

    void testMavenPublishPom() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generatePomFileForMavenPublication", "-PscalaVersions=2.12.1")
            .build()
        def pomXml = new File(projectDir, "build/publications/maven/pom-default.xml").text
        assert !pomXml.contains("_%%")
        assert !pomXml.contains("%scala_version%")
        def root = new XmlSlurper().parseText(pomXml)
        assert root.dependencies.'*'.find { it.artifactId == "scala-library" }.version.text() == '2.12.1'
        assert root.dependencies.'*'.find { it.artifactId == "fake-scala-dep_2.12" } != null
        assert root.artifactId.text() == "codeProject_2.12"
    }

    void testRunOnceTasks() {
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("t1", "t2", "-PscalaVersions=1.1.1,2.2.2", "-PrunOnceTasks=t2")
            .build()
        assert result.taskPaths(SUCCESS).toSorted() == [":t1", ":t2",
                                                        ":recurseWithScalaVersion_2.2.2",
                                                        ":codeProject:t1"].sort()
        assert result.output.contains("t1 1.1.1")
        assert result.output.contains("t1 2.2.2")
        assert result.output.contains("t2 1.1.1")
        assert !result.output.contains("t2 2.2.2")
    }

}
