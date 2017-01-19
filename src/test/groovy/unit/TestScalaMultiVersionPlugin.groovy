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
package unit

import com.adtran.ScalaMultiVersionPlugin
import com.adtran.ScalaMultiVersionPluginExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.GradleBuild
import org.gradle.testfixtures.ProjectBuilder

class TestScalaMultiVersionPlugin extends GroovyTestCase {

    private Project createProject(
            String scalaVersions,
            ScalaMultiVersionPluginExtension extension = new ScalaMultiVersionPluginExtension(),
            Map<String,String> properties = [:])
    {
        Project project = ProjectBuilder.builder()
                .withProjectDir(new File(System.getProperty("user.dir"), "testProjects/simpleProject"))
                .build()
        project.ext.scalaVersions = scalaVersions
        project.ext.scalaMultiVersion = extension
        properties.each { k,v -> project.ext.set(k, v) }
        project.pluginManager.apply(ScalaMultiVersionPlugin)
        project.evaluate()
        return project
    }

    void testPluginApply() {
        def project = createProject("2.12.1")
        assertTrue(project.pluginManager.hasPlugin("com.adtran.scala-multiversion-plugin"))
    }

    void testBaseName() {
        def project = createProject("2.12.1")
        assert project.jar.baseName == "test_2.12"
    }

    void testResolutionStrategy() {
        def project = createProject("2.12.1")
        def conf = project.configurations.getByName("compile").resolvedConfiguration.lenientConfiguration
        assert conf.unresolvedModuleDependencies.size() == 0
        def deps = conf.getAllModuleDependencies()
        assert deps.size() == 3
        deps.each { assert !it.name.contains("_%%") }
        deps.each { assert !it.moduleVersion.contains("scalaVersion") }
    }

    void testBadScalaVersions() {
        [ [null, "Must set 'scalaVersions' property."],
          ["", "Invalid scala version '' in 'scalaVersions' property."],
          ["2.12", "Invalid scala version '2.12' in 'scalaVersions' property."],
        ].each {
            def (scalaVersions, error_msg) = it
            def msg = shouldFailWithCause(GradleException) { createProject(scalaVersions) }
            assert msg.contains(error_msg)
        }
    }

    void testBadDefaultScalaVersions() {
        [ ["", "Invalid scala version '' in 'defaultScalaVersions' property."],
          ["2.12", "Invalid scala version '2.12' in 'defaultScalaVersions' property."],
        ].each {
            def (ver, error_msg) = it
            def msg = shouldFailWithCause(GradleException) {
                def project = createProject("2.12.1", new ScalaMultiVersionPluginExtension(), ["defaultScalaVersions":ver])
                project.ext.defaultScalaVersions
            }
            assert msg.contains(error_msg)
        }
    }

    void testSingleVersion() {
        def project = createProject("2.12.1")
        assert project.ext.scalaVersions == ["2.12.1"]
        assert project.ext.scalaVersion == "2.12.1"
        assert project.ext.scalaSuffix == "_2.12"
    }

    void testMultipleVersions() {
        // comma-separated lists, with or without whitespace, should be allowed
        ["2.12.1,2.11.8", "2.12.1, 2.11.8"].each {
            def project = createProject(it)
            assert project.ext.scalaVersions == ["2.12.1", "2.11.8"]
            assert project.ext.scalaVersion == "2.12.1"
            assert project.ext.scalaSuffix == "_2.12"
            assert project.gradle.startParameter.taskNames == [":recurseWithScalaVersion_2.11.8"]
        }
    }

    void testDefaultScalaVersions() {
        def project = createProject( "2.12.1,2.11.8",
                                     new ScalaMultiVersionPluginExtension(),
                                     ["defaultScalaVersions":"2.11.8"] )
        assert project.ext.scalaVersion == "2.11.8"
    }

    void testAllScalaVersions() {
        def project = createProject( "2.12.1,2.11.8",
                                     new ScalaMultiVersionPluginExtension(),
                                     ["defaultScalaVersions":"2.11.8", "allScalaVersions":true] )
        assert project.ext.scalaVersion == "2.12.1"
        assert project.gradle.startParameter.taskNames == [":recurseWithScalaVersion_2.11.8"]
    }

    void testTasks() {
        def versions = ["2.12.1", "2.11.8"]
        def project = createProject(versions.join(","))
        project.tasks.withType(GradleBuild).each { assert it.tasks == [] }
        versions.tail().each {
            def task = project.tasks.getByName("recurseWithScalaVersion_$it")
            assert task instanceof GradleBuild
        }
    }

    void testExtension() {
        def extension = new ScalaMultiVersionPluginExtension(
                scalaVersionPlaceholder: "<<sv>>",
                scalaSuffixPlaceholder: "_##"
        )
        def project = createProject("2.12.1", extension)
        def conf = project.configurations.getByName("compile").resolvedConfiguration.lenientConfiguration
        assert conf.unresolvedModuleDependencies.size() == 2
        assert conf.getAllModuleDependencies().size() == 1
    }

}
