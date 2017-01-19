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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.plugins.ExtraPropertiesExtension.UnknownPropertyException

class ScalaMultiVersionPlugin implements Plugin<Project> {
    private Project project

    void apply(Project project) {
        this.project = project
        setExtension()
        calculateScalaVersions()
        setResolutionStrategy()
        addTasks()
        addSuffixToJars()
    }

    private void setExtension() {
        project.extensions.create("scalaMultiVersion", ScalaMultiVersionPluginExtension)
    }

    private boolean validateScalaVersion(String scalaVersion) {
        def m = scalaVersion =~ /\d+\.\d+\.\d+/
        return m.matches()
    }

    private String scalaVersionToSuffix(String scalaVersion) {
        def m = scalaVersion =~ /(\d+\.\d+)\.\d+/
        m.matches()
        return "_" + m.group(1)
    }

    private List<String> parseScalaVersionList(propertyName) {
        try {
            def versions = project.ext.get(propertyName).split(",").collect{it.trim()}
            def firstInvalid = versions.find { !validateScalaVersion(it) }
            if (firstInvalid != null) {
                throw new GradleException(
                    "Invalid scala version '$firstInvalid' in '$propertyName' property. " +
                    "Please specify full X.Y.Z scala versions in 'scalaVersions' property.")
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
        project.allprojects { p ->
            p.configurations.all { conf ->
                conf.resolutionStrategy.eachDependency { replaceScalaVersions(it) }
            }
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
        if (!project.ext.has("recursed")) {
            def buildVersionTasks = recurseScalaVersions.collect { ver ->
                project.tasks.create("recurseWithScalaVersion_$ver", GradleBuild) {
                    startParameter = project.gradle.startParameter.newInstance()
                    startParameter.projectProperties["scalaVersion"] = ver
                    startParameter.projectProperties["recursed"] = true
                    tasks = project.gradle.startParameter.taskNames
                }
            }
            def tasksToAdd = buildVersionTasks.collect{ it.path }
            project.gradle.startParameter.taskNames += tasksToAdd
        }
    }

    private void addSuffixToJars() {
        project.allprojects { p ->
            p.afterEvaluate {
                p.tasks.withType(Jar) { t ->
                    t.baseName += p.rootProject.ext.scalaSuffix
                }
            }
        }
    }
}
