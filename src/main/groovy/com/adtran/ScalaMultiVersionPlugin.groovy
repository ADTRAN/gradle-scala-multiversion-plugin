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
package com.adtran

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.plugins.ExtraPropertiesExtension.UnknownPropertyException
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar

class ScalaMultiVersionPlugin implements Plugin<Project> {
    private Project project
    private List<String> defaultRunOnceTasks = ["clean"]

    void apply(Project project) {
        this.project = project
        setExtension()
        calculateScalaVersions()
        setResolutionStrategy()
        addTasks()
        addSuffixToJars()
        resolvePomDependencies()
    }

    private void setExtension() {
        project.extensions.create("scalaMultiVersion", ScalaMultiVersionPluginExtension)
    }

    private boolean validateScalaVersion(String scalaVersion) {
        return scalaVersion ==~ project.scalaMultiVersion.scalaVersionRegex
    }

    private String scalaVersionToSuffix(String scalaVersion) {
        def m = scalaVersion =~ project.scalaMultiVersion.scalaVersionRegex
        m.matches()
        return "_" + m.group("base")
    }

    private List<String> parseScalaVersionList(propertyName) {
        try {
            def versions = project.ext.get(propertyName).split(",").collect{it.trim()}
            def firstInvalid = versions.find { !validateScalaVersion(it) }
            if (firstInvalid != null) {
                throw new GradleException(
                    "Invalid scala version '$firstInvalid' in '$propertyName' property. " +
                    "Please specify full scala versions, matching pattern ${project.scalaMultiVersion.scalaVersionRegex}.")
            }
            return versions
        } catch (NullPointerException | UnknownPropertyException e) {
            return null
        }
    }

    private void calculateScalaVersions() {
        project.ext.scalaVersions = parseScalaVersionList("scalaVersions")
        if (project.ext.scalaVersions == null) {
            throw new GradleException("Must set 'scalaVersions' property.")
        }
        project.ext.defaultScalaVersions = parseScalaVersionList("defaultScalaVersions")
    }

    private void replaceScalaVersions(DependencyResolveDetails details) {
        def newName = details.requested.name.replace(
                project.scalaMultiVersion.scalaSuffixPlaceholder,
                project.ext.scalaSuffix)
        def newVersion = details.requested.version.replace(
                project.scalaMultiVersion.scalaVersionPlaceholder,
                project.ext.scalaVersion)
        def newTarget = "$details.requested.group:$newName:$newVersion"
        if(newTarget != details.requested.toString()) {
            // unnecessarily calling `useTarget` seemed to cause problems in some cases,
            // particularly with `project(...)`-style dependencies.
            details.useTarget(newTarget)
        }
    }

    private void setResolutionStrategy() {
        project.configurations.all { conf ->
            conf.resolutionStrategy.eachDependency { replaceScalaVersions(it) }
        }
    }

    private List<String> determineScalaVersions() {
        def scalaVersions = []
        if (project.ext.has("allScalaVersions")) {
            scalaVersions = project.ext.scalaVersions
        } else if (project.ext.defaultScalaVersions) {
            scalaVersions = project.ext.defaultScalaVersions
        } else {
            scalaVersions = project.ext.scalaVersions
        }
        if (!project.ext.has("scalaVersion")) {
            project.ext.scalaVersion = scalaVersions.head()
        }
        project.ext.scalaSuffix = scalaVersionToSuffix(project.ext.scalaVersion)
        return scalaVersions.tail()
    }

    private void addTasks() {
        def recurseScalaVersions = determineScalaVersions()
        project.afterEvaluate {
            if (!project.ext.has("recursed") && !project.gradle.ext.has("recursionTaskAdded")) {
                def buildVersionTasks = recurseScalaVersions.collect { ver ->
                    project.tasks.create("recurseWithScalaVersion_$ver", GradleBuild) {
                        startParameter = project.gradle.startParameter.newInstance()
                        startParameter.projectProperties["scalaVersion"] = ver
                        startParameter.projectProperties["recursed"] = true
                        startParameter.excludedTaskNames = getRunOnceTasks() +
                           project.gradle.startParameter.excludedTaskNames
                        tasks = project.gradle.startParameter.taskNames
                    }
                }
                def tasksToAdd = buildVersionTasks.collect { it.path }
                project.gradle.startParameter.taskNames += tasksToAdd
                project.gradle.ext.recursionTaskAdded = true
            }
        }
    }

    private List<String> getRunOnceTasks() {
        def runOnceTasks = defaultRunOnceTasks
        if (project.ext.has("runOnceTasks"))
            runOnceTasks = project.ext.runOnceTasks.split(",").collect{it.trim()}
        runOnceTasks.findAll { project.tasks.findByPath(it) }
    }

    private void addSuffixToJars() {
        project.afterEvaluate {
            project.tasks.withType(Jar) { t ->
                t.baseName += project.ext.scalaSuffix
            }
        }
    }

    // Logic for the following function was adapted from nebula-publish-plugin. See NOTICE for
    // details.
    private void resolveMavenPomDependencies(XmlProvider xml) {
        project.plugins.withType(JavaBasePlugin) {
            def dependencies = xml.asNode()?.dependencies?.dependency
            def dependencyMap = [:]

            dependencyMap['compile'] =
                project.configurations.compile.incoming.resolutionResult.allDependencies
            dependencyMap['runtime'] =
                project.configurations.runtime.incoming.resolutionResult.allDependencies
            dependencyMap['test'] =
                project.configurations.testRuntime.incoming.resolutionResult.allDependencies
            dependencies?.each { Node dep ->
                def group = dep.groupId.text()
                def name = dep.artifactId.text()
                def scope = dep.scope.text()

                if (scope == 'provided') {
                    scope = 'runtime'
                }

                def rewriteDepNode = { ResolvedDependencyResult r, boolean addSuffixToName ->
                    def versionNode = dep.version
                    if (!versionNode) {
                        dep.appendNode('version')
                    }
                    def moduleVersion = r.selected.moduleVersion
                    def suffix = addSuffixToName ? project.ext.scalaSuffix : ""
                    dep.groupId[0].value = moduleVersion.group
                    dep.artifactId[0].value = moduleVersion.name + suffix
                    dep.version[0].value = moduleVersion.version
                }

                def matchesModuleComponent = { ResolvedDependencyResult r ->
                    (r.requested instanceof ModuleComponentSelector) &&
                         (r.requested.group == group) &&
                         (r.requested.module == name)
                }

                def matchesProjectComponent = { ResolvedDependencyResult r ->
                    if ( r.requested instanceof ProjectComponentSelector &&
                         r.requested.projectPath.contains(name) )
                    {
                        def depProject = project.findProject(r.requested.projectPath)
                        if (depProject && depProject.plugins.hasPlugin(this.class)) {
                            return true
                        }
                    }
                    return false
                }

                def handleDep = { ResolvedDependencyResult r ->
                    if (matchesModuleComponent(r)) {
                        rewriteDepNode(r, false)
                        return true
                    } else if (matchesProjectComponent(r)) {
                        rewriteDepNode(r, true)
                        return true
                    }
                    return false
                }

                dependencyMap[scope].find(handleDep)

            }
        }
    }

    private void resolvePomDependencies() {
        project.afterEvaluate {
            // for projects using the maven plugin
            project.tasks.withType(Upload).collectMany {
                it.repositories.withType(MavenResolver)
            }.each { resolver ->
                def poms = resolver.activePomFilters.collect { filter ->
                    (filter.name == "default") ? resolver.pom : resolver.pom(filter.name)
                }
                poms.each { pom -> pom.withXml { resolveMavenPomDependencies(it) } }
            }
            // for projects using the maven-publish plugin
            if (project.plugins.hasPlugin("maven-publish")) {
                project.publishing.publications.withType(MavenPublication) {
                    pom.withXml { resolveMavenPomDependencies(it) }
                    artifactId += project.ext.scalaSuffix
                }
            }
        }
    }

}
