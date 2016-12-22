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
        calculateScalaVersions()
        setResolutionStrategy()
        setBaseName()
        addTasks()
    }

    private void setExtension() {
        project.extensions.create("scalaMultiVersion", ScalaMultiVersionPluginExtension)
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
        project.tasks.create("buildMultiVersion")

        project.ext.scalaVersions.each { ver ->
            def newTask = project.tasks.create("build_$ver", GradleBuild) {
                startParameter.projectProperties = [scalaVersion: ver]
                tasks = project.scalaMultiVersion.buildTasks
            }
            project.tasks.buildMultiVersion.dependsOn(newTask)
        }
    }
}
