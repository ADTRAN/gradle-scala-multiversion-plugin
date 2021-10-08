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

class ScalaMultiVersionPluginExtension {
    String scalaVersionPlaceholder = "%scala-version%"
    String scalaSuffixPlaceholder = "_%%"
    String scala3SuffixPlaceholder = "%_3%"
    String scala3BasePlaceholder = "%3%"
    
    String scalaVersionRegex = /(?<base>\d+\.\d+)\.\d+(-(RC|M)\d+)?/
    void setScalaVersionRegex(scalaVersionRegex) {
        assert (scalaVersionRegex ==~ /.*\(\?<base>.+?\).*/) : "Scala version regex should include <base> named group for scala compiler base version"
        this.scalaVersionRegex = scalaVersionRegex
    }
}
