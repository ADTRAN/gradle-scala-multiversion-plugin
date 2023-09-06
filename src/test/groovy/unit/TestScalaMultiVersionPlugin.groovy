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
import common.GradleMetadataUtil
import common.SimpleProjectTest
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.internal.GradleModuleMetadataWriter
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.GradleBuild
import org.gradle.testfixtures.ProjectBuilder

class TestScalaMultiVersionPlugin extends GroovyTestCase implements SimpleProjectTest {

    private Project createProject(String scalaVersions, Closure config = {})
    {
        Project project = ProjectBuilder.builder()
                .withProjectDir(new File(System.getProperty("user.dir"), "testProjects/simpleProject"))
                .build()
        project.ext.scalaVersions = scalaVersions
        config.delegate = project
        config(project)
        project.pluginManager.apply(ScalaMultiVersionPlugin)
        project.evaluate()
        return project
    }

    void testPluginApply() {
        def project = createProject("2.12.1")
        assertTrue(project.pluginManager.hasPlugin("com.adtran.scala-multiversion-plugin"))
    }

    void testBaseName() {
        [ ["2.12.1", "2.12"],
          ["2.13.0-M5", "2.13"]
        ].each {
            def (ver, base) = it
            def project = createProject(ver)
            assert project.jar.archiveBaseName.get() == "test_$base"
        }
    }

    void testDependencyResolution() {
        def project = createProject("2.12.1")
        def conf = project.configurations.getByName("compileClasspath").resolvedConfiguration.lenientConfiguration
        assert conf.unresolvedModuleDependencies.size() == 0
        def deps = conf.getAllModuleDependencies()
        assert deps.size() == 4
        deps.each { assert !it.name.contains("_%%") }
        deps.each { assert !it.moduleVersion.contains("%scala-version%") }
    }

    void testConstraintResolution() {
        def project = createProject("2.12.1")
        def conf = project.configurations.getByName("implementation")
        conf.dependencyConstraints.each {assert !it.name.contains("_%%") }
        conf.dependencyConstraints.each {assert !it.name.contains("%scala-version%") }
        def resolvedConstraint = conf.dependencyConstraints.find { it.name == "fake-scala-dep_2.12" }
        assert resolvedConstraint != null
        def versionConstraint = resolvedConstraint.versionConstraint
        assert versionConstraint.preferredVersion == "1.2.4"
        assert versionConstraint.requiredVersion == "[1.2, 1.3["
        assert versionConstraint.rejectedVersions == ["1.2.5"]
        assert resolvedConstraint.reason == "foo"
    }

    void testDependencyWithConstraintResolved() {
        def project = createProject("2.12.1")
        def conf = project.configurations.getByName("compileClasspath")
        def resolvedDependencies = conf.resolvedConfiguration.firstLevelModuleDependencies
        def resolvedDependency = resolvedDependencies.find {it.module.id.name == "fake-scala-dep_2.12" }
        assert resolvedDependency != null
        assert resolvedDependency.moduleVersion == "1.2.3"
    }

    void testUnresolvableConstraint() {
        def addUnresolvableConstraint = {
            afterEvaluate {
                dependencies {
                    constraints {
                        implementation("org.scalamacros:fake-compiler-plugin_%scala-version%") {
                            version {
                                reject "1.2.3"
                            }
                        }
                    }
                }
            }
        }
        def project = createProject("2.12.1", addUnresolvableConstraint)
        def conf = project.configurations.getByName("compileClasspath").resolvedConfiguration.lenientConfiguration
        assert !conf.unresolvedModuleDependencies.isEmpty()
        conf.unresolvedModuleDependencies.each {assert !it.selector.name.contains("%scala-version%") }
    }

    void testBadScalaVersions() {
        [ [null, "Must set 'scalaVersions' property."],
          ["", "Invalid scala version '' in 'scalaVersions' property."],
          ["2.12", "Invalid scala version '2.12' in 'scalaVersions' property."],
          ["2.13.0-SNAPSHOT", "Invalid scala version '2.13.0-SNAPSHOT' in 'scalaVersions' property."],
          ["2.13.0-rc1", "Invalid scala version '2.13.0-rc1' in 'scalaVersions' property."],
        ].each {
            def (scalaVersions, error_msg) = it
            def msg = shouldFailWithCause(GradleException) { createProject(scalaVersions) }
            assert msg.contains(error_msg)
        }
    }

    void testBadDefaultScalaVersions() {
        [ ["", "Invalid scala version '' in 'defaultScalaVersions' property."],
          ["2.12", "Invalid scala version '2.12' in 'defaultScalaVersions' property."],
          ["2.13.0-SNAPSHOT", "Invalid scala version '2.13.0-SNAPSHOT' in 'defaultScalaVersions' property."],
          ["2.13.0-rc1", "Invalid scala version '2.13.0-rc1' in 'defaultScalaVersions' property."],
        ].each {
            def (ver, error_msg) = it
            def msg = shouldFailWithCause(GradleException) {
                def project = createProject("2.12.1") {
                    ext.defaultScalaVersions = ver
                }
                project.ext.defaultScalaVersions
            }
            assert msg.contains(error_msg)
        }
    }

    void testSingleVersion() {
        [ ["2.12.1", "_2.12"],
          ["2.13.0-M5", "_2.13"]
        ].each {
            def (ver, base) = it
            def project = createProject(ver)
            assert project.ext.scalaVersions == [ver]
            assert project.ext.scalaVersion == ver
            assert project.ext.scalaSuffix == base
        }
    }

    void testMultipleVersions() {
        // comma-separated lists, with or without whitespace, should be allowed
        ["2.13.0-M5,2.12.1,2.11.8", "2.13.0-M5, 2.12.1, 2.11.8"].each {
            def project = createProject(it)
            assert project.ext.scalaVersions == ["2.13.0-M5", "2.12.1", "2.11.8"]
            assert project.ext.scalaVersion == "2.13.0-M5"
            assert project.ext.scalaSuffix == "_2.13"
            assert project.gradle.startParameter.taskNames == [":recurseWithScalaVersion_2.12.1", ":recurseWithScalaVersion_2.11.8"]
        }
    }

    void testDefaultScalaVersions() {
        def project = createProject("2.13.0-M5,2.12.1,2.11.8") {
             ext.defaultScalaVersions = "2.11.8"
        }
        assert project.ext.scalaVersion == "2.11.8"
    }

    void testAllScalaVersions() {
        def project = createProject("2.13.0-M5,2.12.1,2.11.8") {
            ext.defaultScalaVersions = "2.11.8"
            ext.allScalaVersions = true
        }
        assert project.ext.scalaVersion == "2.13.0-M5"
        assert project.gradle.startParameter.taskNames == [":recurseWithScalaVersion_2.12.1", ":recurseWithScalaVersion_2.11.8"]
    }

    void testTasks() {
        def versions = ["2.13.0-M5, 2.12.1", "2.11.8"]
        def project = createProject(versions.join(","))
        project.tasks.withType(GradleBuild).each { assert it.tasks == [] }
        versions.tail().each {
            def task = project.tasks.getByName("recurseWithScalaVersion_$it")
            assert task instanceof GradleBuild
        }
    }

    void testExtension() {
        def project = createProject("2.12.1") {
            ext.scalaMultiVersion = new ScalaMultiVersionPluginExtension(
                scalaVersionPlaceholder: "<<sv>>",
                scalaSuffixPlaceholder: "_##",
                scalaVersionRegex: /(?<base>2\.12)\.1/
            )
        }
        def conf = project.configurations.getByName("compileClasspath").resolvedConfiguration.lenientConfiguration
        assert conf.unresolvedModuleDependencies.size() == 4 // 3 dependencies + 1 constraint
        assert conf.getAllModuleDependencies().size() == 1
    }

    void testScalaExtensionVersionWithoutBase() {
        def msg = shouldFail AssertionError, {
            def project = createProject("2.12.1") {
                ext.scalaMultiVersion = new ScalaMultiVersionPluginExtension(
                    scalaVersionRegex: /no base/
                )
            }
        }
        assert msg.contains("Scala version regex should include <base> named group for scala compiler base version")
    }

    void testMavenPom() {
        [ ["2.12.1", "2.12"],
          ["2.13.0-M5", "2.13"]
        ].each {
            def (ver, base) = it
            def project = createProject(ver)
            File pomXmlFile = File.createTempFile("temp",".pom")
            def t = project.tasks.generatePomFileForMavenPublication
            t.setDestination(pomXmlFile)
            t.doGenerate()
            def pomXml = pomXmlFile.text
            assert !pomXml.contains("_%%")
            assert !pomXml.contains("%scala_version%")
            def root = new XmlSlurper().parseText(pomXml)
            assert root.dependencies.'*'.find { it.artifactId == "scala-library" }.version.text() == ver
            assert root.dependencies.'*'.find { it.artifactId == "fake-scala-dep_$base" } != null
        }
    }

    void testGradleModuleMetadata() {
        [ ["2.12.1", "2.12"],
          ["2.13.0-M5", "2.13"]
        ].each {
            def (ver, base) = it
            def project = createProject(ver)
            File moduleMetadataFile = File.createTempFile("temp", ".module")
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(moduleMetadataFile), "utf8"));
            GradleModuleMetadataWriter metadataWriter = GradleMetadataUtil.createMetadataWriter()
            def publications = project.publishing.publications.withType(MavenPublication.class)
            publications.all { publication ->
                metadataWriter.generateTo(publication, publications, writer)
                def metadataJsonStr = moduleMetadataFile.text
                assert !metadataJsonStr.contains("_%%")
                assert !metadataJsonStr.contains("%scala_version%")
                def metadataJson = new JsonSlurper().parseText(metadataJsonStr)
                def runtimeDependencies = metadataJson.variants.find { it.name == "runtimeElements" }.dependencies
                assert runtimeDependencies.find { it.module == "scala-library" }.version.requires == ver
                assert runtimeDependencies.find { it.module == "fake-scala-dep_$base" } != null
            }
        }
    }

}
