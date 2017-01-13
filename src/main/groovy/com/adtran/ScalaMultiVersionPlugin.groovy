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

class ScalaMultiVersionPlugin implements Plugin<Project> {
    private Project project

    void apply(Project project) {
        this.project = project
        setExtension()
        overrideExtensionFromProperties()
        calculateScalaVersions()
        setResolutionStrategy()
        setBaseName()
        addTasks()
    }

    private void setExtension() {
        project.extensions.create("scalaMultiVersion", ScalaMultiVersionPluginExtension)
    }

    private void overrideExtensionFromProperties() {
        project.afterEvaluate {
            if (project.ext.has('buildTasks')) {
                project.scalaMultiVersion.buildTasks = project.ext.buildTasks.split(",").collect{it.trim()}
            }
        }
    }

    private String scalaVersionToSuffix(String scalaVersion) {
        def m = scalaVersion =~ /(\d+\.\d+)\.\d+/
        if (m.matches() && m.groupCount() == 1) {
            return "_" + m.group(1)
        } else {
            throw new GradleException("""Invalid scala version "$scalaVersion". Please specify full X.Y.Z scala versions in `scalaVersions` property.""")
        }
    }

    private void calculateScalaVersions() {
        try {
            project.ext.scalaVersions = project.ext.scalaVersions.split(",").collect{it.trim()}
        } catch (Exception e) {
            throw new GradleException("""Must set `scalaVersions` property.""")
        }
        project.ext.scalaSuffixes = project.ext.scalaVersions.collect { scalaVersionToSuffix(it) }
        if (!project.ext.has("scalaVersion")) project.ext.scalaVersion = project.ext.scalaVersions[0]
        project.ext.scalaSuffix = scalaVersionToSuffix(project.ext.scalaVersion)
    }

    private void replaceScalaVersions(DependencyResolveDetails details) {
        def newName = details.requested.name.replace(project.scalaMultiVersion.scalaSuffixPlaceholder, project.ext.scalaSuffix)
        def newVersion = details.requested.version.replace(project.scalaMultiVersion.scalaVersionPlaceholder, project.ext.scalaVersion)
        details.useTarget("$details.requested.group:$newName:$newVersion")
    }

    private void setResolutionStrategy() {
        project.configurations.all { Configuration conf ->
            conf.resolutionStrategy.eachDependency { replaceScalaVersions(it) }
        }
    }

    private void setBaseName() {
        project.jar.baseName += project.ext.scalaSuffix
    }

    private void addTasks() {
        project.afterEvaluate {
            project.tasks.create("buildMultiVersion")

            project.ext.scalaVersions.each { ver ->
                def newTask = project.tasks.create("build_$ver", GradleBuild) {
                    startParameter = project.gradle.startParameter.newInstance()
                    startParameter.projectProperties["scalaVersion"] = ver
                    tasks = project.scalaMultiVersion.buildTasks
                }
                project.tasks.buildMultiVersion.dependsOn(newTask)
            }
        }
    }
}
