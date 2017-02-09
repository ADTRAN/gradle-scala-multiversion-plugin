package common

trait SimpleProjectTest {
    def projectDir = new File(System.getProperty("user.dir"), "testProjects/simpleProject")
    def buildDir = new File(projectDir, "build")
    def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")

    void setUp() {
        tearDown()
        // write the plugin classpath to a file where the test project can find it. This is
        // the best workaround I could find for keeping the plugin under test on the classpath given
        // the recursive nature of the builds.
        buildDir.mkdirs()
        new File(buildDir, "plugin-classpath.txt").text = pluginClasspathResource.text
    }

    void tearDown() {
        if (buildDir.exists()) buildDir.deleteDir()
    }
}
