/* Copyright 2004-2007 Brian McCallister
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

package org.skife.jdbi.v2.unstable.stringtemplate;

import junit.framework.TestCase;

public class TestClasspathGroupLoader extends TestCase
{
    private ClasspathGroupLoader loader;

    public void setUp() throws Exception
    {
        loader = new ClasspathGroupLoader();
    }

    public void testEmptyClasspath()
    {
        assertEquals("demo", loader. buildFilename(null, "demo"));
        assertEquals("demo", loader.buildFilename("", "demo"));
    }

    public void testTrailingSlashClasspath()
    {
        assertEquals("demo", loader.buildFilename("/", "demo"));
        assertEquals("path/demo", loader.buildFilename("path/", "demo"));
    }

    public void testNameClasspath()
    {
        assertEquals("path/demo", loader.buildFilename("path", "demo"));
        assertEquals("path/demo", loader.buildFilename("/path", "demo"));
    }
}
