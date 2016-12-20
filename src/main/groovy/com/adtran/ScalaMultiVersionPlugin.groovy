package com.adtran

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.tasks.GradleBuild

class ScalaMultiVersionPlugin implements Plugin<Project> {
    void apply(Project project) {
        calculate_scala_versions(project)
        set_resolution_strategy(project)
        set_base_name(project)
        add_tasks(project)
    }

    private String scala_version_to_suffix(String scala_version) {
        def m = scala_version =~ /(\d+\.\d+)\.\d+/
        if (m.matches() && m.groupCount() == 1) {
            return "_" + m.group(1)
        } else {
            throw new GradleException("""Invalid scala version "$scala_version". Please specify full X.Y.Z scala versions in scala_versions property.""")
        }
    }

    private void calculate_scala_versions (Project project) {
        try {
            project.ext.scala_versions = project.ext.scala_versions.split(",").collect{it.trim()}
        } catch (Exception e) {
            throw new GradleException("""Must set scala_versions property.""")
        }
        project.ext.scala_suffixes = project.ext.scala_versions.collect { scala_version_to_suffix(it) }
        if (!project.ext.has("scala_version")) project.ext.scala_version = project.ext.scala_versions[0]
        project.ext.scala_suffix = scala_version_to_suffix(project.ext.scala_version)
    }

    private void replaceScalaVersions(DependencyResolveDetails details, Project project) {
        def new_name = details.requested.name.replace("_%%", project.ext.scala_suffix)
        def new_version = details.requested.version.replace("scala_version", project.ext.scala_version)
        println("***** replacing $details.requested with $details.requested.group:$new_name:$new_version")
        details.useTarget("$details.requested.group:$new_name:$new_version")
    }

    private void set_resolution_strategy(Project project) {
        project.configurations.all { Configuration conf ->
            conf.resolutionStrategy.eachDependency { replaceScalaVersions(it, project) }
        }
    }

    private void set_base_name(Project project) {
        project.jar.baseName += project.ext.scala_suffix
    }

    private void add_tasks(Project project) {
        project.tasks.create("buildMultiVersion")

        project.ext.scala_versions.each { ver ->
            def new_task = project.tasks.create("build_$ver", GradleBuild) {
                startParameter.projectProperties = [scala_version: ver]
                tasks = ['build']
            }
            project.tasks.buildMultiVersion.dependsOn(new_task)
        }
    }
}