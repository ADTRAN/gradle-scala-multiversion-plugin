==========================
Scala Multi-Version Plugin
==========================

This Gradle plugin allows a project to build against multiple versions of scala. You declare a list of scala versions
via a project property (e.g. ``scalaVersions = 2.12.1, 2.11.8``), and then declare dependencies like this::

    compile "org.scala-lang:scala-library:%scala-version%"
    compile "org.scala-lang.modules:scala-parser-combinators_%%:1.0.4"

Now when you run gradle tasks, they'll run once for each scala version. If you wish, you can also control which scala
versions get executed by default and override the default from the command line. Your jar file will also get the
appropriate suffix added (e.g. ``my-project_2.12-1.2.3.jar``).

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
numbers. The easiest way to add this property is probably to add something like this to your ``gradle.properties``
file::

    scalaVersions = 2.12.1, 2.11.8

Although you could also set (or override) it via the command line::

    gradle build -PscalaVersions=2.12.1,2.11.8

Updating Dependencies
---------------------

The plugin allows you to use two different placeholders when declaring your dependencies which will then be substituted
with actual values before being resolved. The first is ``%scala-version%`` and can be used as a version number in a
dependency. It will be replaced by the fully-qualified scala version for this build (e.g. ``'2.12.1'``). This is
typically used when declaring your scala-library dependency::

    compile "org.scala-lang:scala-library:%scala-version%"

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

The only required configuration is to set the ``scalaVersions`` property. If this is the only property provided, then
build tasks will be run for all scala versions listed. In some cases, this may not be a desirable default (for example
during development if your build takes some time to execute). If you prefer, you can also set ``defaultScalaVersions``
(it's a comma-separated list) in your ``gradle.properties`` file to set which scala versions will be run by default.
Then, if you want to override the default and build for all versions (e.g. on your CI server), you can set the
``allScalaVersions`` parameter, typically from the command line (``-PallScalaVersions``).

You can also configure the placeholder values if they happen to cause a conflict, or you just like something else
aesthetically. To do so, add the following block in your ``build.gradle`` file::

    scalaMultiVersion {
        scalaVersionPlaceholder = "%scala-version%"
        scalaSuffixPlaceholder = "_%%"
    }

============================  =============  ======================  ===================================================
Property                      Type           Default                 Description
============================  =============  ======================  ===================================================
``scalaVersionPlaceholder``   String         ``'%scala-version%'``   The placeholder used in dependency versions to be
                                                                     replaced by the full scala version (e.g.
                                                                     ``'2.12.8'``)
``scalaSuffixPlaceholder``    String         ``'_%%'``               The placeholder used in dependency module names to
                                                                     be replaced by the scala suffix (e.g. ``'_2.12'``)
============================  =============  ======================  ===================================================

Usage
=====

Just run gradle as usual. Any tasks you specify on the command line will be run once for each scala version selected
(see section `Advanced Configuration`_ for details).

Also potentially useful to your buildscript are several extra properties this plugin attaches to your project:

==================  =============  =====================================================================================
Property            Type           Description
==================  =============  =====================================================================================
``scalaVersion``    String         The scala version that will be used for *this* build. E.g. ``'2.12.1'``.
``scalaSuffix``     String         The scala suffix that will be used for *this* build. E.g. ``'_2.12'``.
==================  =============  =====================================================================================

These could be useful, for example, if you wish to select a different dependency based on the scala version. For
example::

    dependencies {
      if(scalaVersion.startsWith("2.12")) {
        compile "org.whatever:some-dependency:1.2.3"
      } else {
        compile "org.whatever:some-other-dependency:1.2.3"
      }
    }

Examples
--------

Run All Versions by Default
~~~~~~~~~~~~~~~~~~~~~~~~~~~

To run your tasks for all scala versions by default, you would create a ``gradle.properties`` file that only contains
``scalaVersions``::

    scalaVersions = 2.11.8, 2.12.1

Then you could run tasks like this...

* Build all versions: ``gradle build``
* Build one particular version: ``gradle build -PscalaVersions=2.12.1``

Run a Single Version by Default
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you don't want to build for all versions by default, set ``defaultScalaVersions`` in addition to ``scalaVersions`` in
your ``gradle.properties`` file::

    scalaVersions = 2.11.8, 2.12.1
    defaultScalaVersions = 2.12.1

Then run tasks like this...

* Build the default version (2.12.1): ``gradle build``
* Build all versions: ``gradle build -PallScalaVersions``
* Build a single version other than the default (a little strange, I know, but it works):

  ``gradle build -PdefaultScalaVersions=2.11.8``

Known Limitations
=================

Because the artifacts are only differentiated by suffix and they all land in the same folder, if you try to list two
versions in ``scalaVersions`` from the same major version (Scala uses <epoch>.<major>.<minor> versioning), the artifacts
will overwrite each other and only the last one will survive. So for example ``scalaVersions = 2.11.1, 2.11.8`` won't
work as you expect today.

License
=======

This project is licensed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0). Copyright
2017, ADTRAN, Inc.

Contributing
============

Issues and pull requests are welcome if you have bugs/suggestions/improvements!

