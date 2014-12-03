/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.dcaro.hudson.plugins.jythonpostbuild;

import static org.junit.Assert.*;

import java.util.Collections;

import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.jython.SecureJythonScript;
import org.jenkinsci.plugins.scriptsecurity.scripts.ClasspathEntry;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.JythonLanguage;
import org.junit.Rule;
import org.junit.Test;
import org.dcaro.hudson.test.JenkinsRule;
import org.dcaro.hudson.test.recipes.WithPlugin;

public class JythonPostbuildRecorderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    private static final String SCRIPT_FOR_MATRIX = StringUtils.join(new String[]{
            "import hudson.matrix.MatrixBuild;",
            "import hudson.matrix.MatrixRun;",
            "if (manager.buildIsA(MatrixBuild.class)) {",
            "  // codes for matrix parents.",
            "  manager.addShortText(\"parent\");",
            "} else if(manager.buildIsA(MatrixRun)) {",
            "  // codes for matrix children.",
            "  manager.addShortText(manager.getEnvVariable(\"axis1\"));",
            "} else {",
            "  // unexpected case.",
            "  manager.buildFailure();",
            "}"
    }, '\n');

    @Test
    public void testMatrixProjectWithParent() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList().add(new JythonPostbuildRecorder(new SecureJythonScript(SCRIPT_FOR_MATRIX, true, Collections.<ClasspathEntry>emptyList()), 2, true));
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        
        assertEquals("parent", b.getAction(JythonPostbuildAction.class).getText());
        assertEquals("value1", b.getRun(new Combination(axisList, "value1")).getAction(JythonPostbuildAction.class).getText());
        assertEquals("value2", b.getRun(new Combination(axisList, "value2")).getAction(JythonPostbuildAction.class).getText());
    }
    
    @Test
    public void testMatrixProjectWithoutParent() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getPublishersList().add(new JythonPostbuildRecorder(new SecureJythonScript(SCRIPT_FOR_MATRIX, true, Collections.<ClasspathEntry>emptyList()), 2, false));
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        
        assertNull(b.getAction(JythonPostbuildAction.class));
        assertEquals("value1", b.getRun(new Combination(axisList, "value1")).getAction(JythonPostbuildAction.class).getText());
        assertEquals("value2", b.getRun(new Combination(axisList, "value2")).getAction(JythonPostbuildAction.class).getText());
    }
    
    @Test
    @WithPlugin("dependee.hpi") // provides org.jenkinsci.plugins.dependencytest.dependee.Dependee.getValue() which returns "dependee".
    public void testDependencyToAnotherPlugin() throws Exception {
        final String SCRIPT =
                "import org.jenkinsci.plugins.dependencytest.dependee.Dependee;"
                + "manager.addShortText(Dependee.getValue());";
        // as Dependee.getValue isn't whitelisted, we need to approve that.
        ScriptApproval.get().preapprove(SCRIPT, JythonLanguage.get());
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.getPublishersList().add(
                new JythonPostbuildRecorder(
                        new SecureJythonScript(
                                SCRIPT,
                                false,
                                Collections.<ClasspathEntry>emptyList()
                        ),
                        2,
                        false
                )
        );
        
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        assertEquals("dependee", b.getAction(JythonPostbuildAction.class).getText());
    }
}
