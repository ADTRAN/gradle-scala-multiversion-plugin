==========================
Scala Multi-Version Plugin
==========================

This Gradle plugin allows a project to build against multiple versions of scala. You declare a list of scala versions
via a project property (e.g. ``scalaVersions = 2.12.1, 2.11.8``), and then declare dependencies like this::

    compile "org.scala-lang:scala-library:scala-version"
    compile "org.scala-lang.modules:scala-parser-combinators_%%:1.0.4"

The plugin will then add a tasks like ``build_2.12.1`` and ``build_2.11.8`` to build a particular version, along with a
task ``buildMultiVersions`` to build them all!  ``gradle build`` will still just build one version—the first one listed
in ``scalaVersions``. Your jar file will also get the appropriate suffix added (e.g. ``my-project_2.12-1.2.3.jar``).

Setup
=====

Adding the Plugin
-----------------

First, add the appropriate buildscript repository and dependency::

    TODO: instructions for getting the plugin in the buildscript dependencies.

Then apply the plugin using either the old style::

    apply plugin: "com.adtran.scala-multiversion-plugin"

...or the new style::

    plugins {
      id "com.adtran.scala-multiversion-plugin"
    }

Setting Scala Versions
----------------------

Before use, you need to indicate which versions of scala you want to compile for by setting a Gradle property called
``scalaVersions``. It should be a comma-separated list (whitespace optional) of fully-qualified (X.Y.Z) scala version
numbers. The first version in the list will become the "default" version. The easiest way to add this property is
probably to add something like this to your ``gradle.properties`` file::

    scalaVersions = 2.12.1, 2.11.8

Although you could also set (or override) it via the command line::

    gradle build -PscalaVersions=2.12.1,2.11.8

Updating Dependencies
---------------------

The plugin allows you to use two different placeholders when declaring your dependencies which will then be substituted
with actual values before being resolved. The first is ``scala-version`` and can be used as a version number in a
dependency and will be replaced by the fully-qualified scala version for this build (e.g. ``'2.12.1'``). This is
typically used when declaring your scala-library dependency::

    compile "org.scala-lang:scala-library:scala-version"

The second placeholder is ``_%%`` and can be used in a dependency module name to stand for the scala "suffix"
corresponding to this scala version (e.g. ``'_2.12'``). For example::

    compile "org.scala-lang.modules:scala-parser-combinators_%%:1.0.4"

These placeholders can also be configured. See `Advanced Configuration`_.

Other Things
------------

This plugin will automatically append the scala version to the end of your artifact name (e.g.
``my-project_2.11-1.2.3.jar``), so if you previously added that manually (e.g., by setting something like
``rootProject.name="my-project_2.12"`` in ``settings.gradle`` or otherwise), you'll want to undo that now. Otherwise
you'll end up with the suffix appended twice.

Advanced Configuration
----------------------

You can configure some further aspects of the plugin via its configuration extension in your ``build.gradle`` file::

    scalaMultiVersion {
        scalaVersionPlaceholder = "scala-version"
        scalaSuffixPlaceholder = "_%%"
        buildTasks = ['build']
    }

============================  =============  ====================  =====================================================
Property                      Type           Default               Description
============================  =============  ====================  =====================================================
``scalaVersionPlaceholder``   String         ``'scala-version'``   The placeholder used in dependency versions to be
                                                                   replaced the full scala version (e.g. ``'2.12.8'``)
``scalaSuffixPlaceholder``    String         ``'_%%'``             The placeholder used in dependency module names to be
                                                                   replaced by the scala suffix (e.g. ``'_2.12'``)
``buildTasks``                List<String>   ``['build']``         The task(s) used to build your complete project. Will
                                                                   be run once for each scala version specified.
============================  =============  ====================  =====================================================

Usage
=====

This plugin will add a number of tasks and properties to your project. The most important of these is the task
``buildMultiVersion``. This will build your project once for each version of scala indicated in the ``scalaVersions``
property (see `Setting Scala Versions`_). It also adds individual build tasks for each version of scala named
``build_<scala-version>`` (e.g., ``build_2.12.1``). These can be used to build a single particular version (must be
listed in ``scalaVersions``, of course). A simple ``gradle build`` will use whichever version of scala is listed first
in ``scalaVersions``.

Also potentially useful to your buildscript are several extra properties this plugin attaches to your project:

==================  =============  =====================================================================================
Property            Type           Description
==================  =============  =====================================================================================
``scalaVersions``   List<String>   The list originally specified as a comma-separated string gets turned into an actual
                                   List<String>. E.g., ``['2.12.1', '2.11.8']``
``scalaSuffixes``   List<String>   The suffixes corresponding to each scala version listed. E.g. ``['_2.12', '_2.11']``.
``scalaVersion``    String         The scala version that will be used for *this* build. E.g. ``'2.12.1'``.
``scalaSuffix``     String         The scala suffix that will be used for *this* build. E.g. ``'_2.12'``.
==================  =============  =====================================================================================

Known Limitations
=================

Because the artifacts are only differentiated by suffix and they all land in the same folder, if you try to list two
versions in ``scalaVersions`` from the same major version (Scala uses <epoch>.<major>.<minor> versioning), the artifacts
will overwrite each other and only the last one will survive. So for example ``scalaVersions = 2.11.1, 2.11.8`` won't
work as you expect today.

Contributing
============

Issues and pull requests are welcome if you have bugs/suggestions/improvements!
