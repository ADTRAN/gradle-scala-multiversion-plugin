import com.adtran.ScalaMultiVersionPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.api.tasks.GradleBuild
import org.gradle.testfixtures.ProjectBuilder

class TestScalaMultiVersionPlugin extends GroovyTestCase {

    private Project createProject(String scala_versions) {
        Project project = ProjectBuilder.builder()
                .withProjectDir(new File("""${System.getProperty("user.dir")}/testProjects/codeProject"""))
                .withName("codeProject")
                .build()
        project.ext.scala_versions = scala_versions
        project.pluginManager.apply("java")
        project.pluginManager.apply(ScalaMultiVersionPlugin)
        project.evaluate()
        return project
    }

    void setUp() {
    }

    void tearDown() {}

    void testBadScalaVersions() {
        [ [null, "Must set scala_versions property."],
          ["", "Invalid scala version"],
          ["2.12", "Invalid scala version"]
        ].each {
            def (scala_versions, error_msg) = it
            def msg = shouldFailWithCause(GradleException) { createProject(scala_versions) }
            assert msg.contains(error_msg)
        }
    }

    void testPluginApply() {
        def project = createProject("2.12.1")
        assertTrue(project.pluginManager.hasPlugin("com.adtran.scala-multiversion-plugin"))
    }

    void testSingleVersion() {
        def project = createProject("2.12.1")
        assert ["2.12.1"] == project.ext.scala_versions
        assert ["_2.12"] == project.ext.scala_suffixes
        assert "2.12.1" == project.ext.scala_version
        assert "_2.12" == project.ext.scala_suffix
    }

    void testMultipleVersions() {
        // comma-separated lists, with or without whitespace, should be allowed
        ["2.12.1,2.11.8", "2.12.1, 2.11.8"].each {
            def project = createProject(it)
            assert ["2.12.1", "2.11.8"] == project.ext.scala_versions
            assert ["_2.12", "_2.11"] == project.ext.scala_suffixes
            assert "2.12.1" == project.ext.scala_version
            assert "_2.12" == project.ext.scala_suffix
        }
    }

    void testBaseName() {
        def project = createProject("2.12.1")
        assert project.jar.baseName.endsWith("_2.12")
    }

    void testTasks() {
        def versions = ["2.12.1", "2.11.8"]
        def project = createProject(versions.join(","))
        assert project.tasks.buildMultiVersion instanceof DefaultTask
        versions.each {
            def task = project.tasks.getByName("build_$it")
            assert task instanceof GradleBuild
            assert project.tasks.buildMultiVersion.getDependsOn().contains(task)
        }
    }

    void testResolutionStrategy() {
        def project = createProject("2.12.1")
        def conf = project.configurations.getByName("compile").resolvedConfiguration
        assert conf.lenientConfiguration.unresolvedModuleDependencies.size() == 0
        def deps = conf.lenientConfiguration.getAllModuleDependencies()
        deps.each { assert !it.name.contains("_%%") }
        deps.each { assert !it.moduleVersion.contains("scala_version") }
    }
}
