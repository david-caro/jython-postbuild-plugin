/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Serban Iordache
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

import org.python.util.PythonInterpreter;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jenkins.model.Jenkins;

/** This class associates {@link JythonPostbuildAction}s to a build. */
@SuppressWarnings("unchecked")
public class JythonPostbuildRecorder extends Recorder implements MatrixAggregatable {
    private static final Logger LOGGER = Logger.getLogger(JythonPostbuildRecorder.class.getName());

    private String script;
    private final int behavior;
    private final boolean runForMatrixParent;

    public static class BadgeManager {
        private AbstractBuild<?, ?> build;
        private final BuildListener listener;
        private final Result scriptFailureResult;
        private final Set<AbstractBuild<?, ?>> builds = new HashSet<AbstractBuild<?,?>>();
        private EnvVars envVars;

        public BadgeManager(AbstractBuild<?, ?> build, BuildListener listener, Result scriptFailureResult) {
            setBuild(build);
            try {
                this.envVars = build.getEnvironment(listener);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace(listener.getLogger());
            } catch (IOException e){
                e.printStackTrace(listener.getLogger());
            }
            this.listener = listener;
            this.scriptFailureResult = scriptFailureResult;
        }

        public EnvVars getEnvVars(){
            return this.envVars;
        }

        public void println(String string){
            this.listener.getLogger().println(string);
        }

        public String getEnvVariable(String key) throws IOException, InterruptedException{
            return this.envVars.get(key);
        }

        public Hudson getHudson() {
            return Hudson.getInstance();
        }

        public AbstractBuild<?, ?> getBuild() {
            return build;
        }
        public void setBuild(AbstractBuild<?, ?> build) {
            if(build != null) {
                this.build = build;
                builds.add(build);
            }
        }
        public boolean setBuildNumber(int buildNumber) {
            AbstractBuild<?, ?> newBuild = build.getProject().getBuildByNumber(buildNumber);
            setBuild(newBuild);
            return (newBuild != null);
        }

        public BuildListener getListener() {
            return listener;
        }

        public void addShortText(String text) {
            build.getActions().add(JythonPostbuildAction.createShortText(text));
        }

        public void addShortText(String text, String color, String background, String border, String borderColor) {
            build.getActions().add(JythonPostbuildAction.createShortText(text, color, background, border, borderColor));
        }

        public void addBadge(String icon, String text) {
            build.getActions().add(JythonPostbuildAction.createBadge(icon, text));
        }

        public void addBadge(String icon, String text, String link) {
            build.getActions().add(JythonPostbuildAction.createBadge(icon, text, link));
        }

        public void addInfoBadge(String text) {
            build.getActions().add(JythonPostbuildAction.createInfoBadge(text));
        }

        public void addWarningBadge(String text) {
            build.getActions().add(JythonPostbuildAction.createWarningBadge(text));
        }

        public void addErrorBadge(String text) {
            build.getActions().add(JythonPostbuildAction.createErrorBadge(text));
        }

        public void removeBadges() {
            List<Action> actions = build.getActions();
            List<JythonPostbuildAction> badgeActions = build.getActions(JythonPostbuildAction.class);
            for(JythonPostbuildAction action : badgeActions) {
                actions.remove(action);
            }
        }

        public void removeBadge(int index) {
            List<Action> actions = build.getActions();
            List<JythonPostbuildAction> badgeActions = build.getActions(JythonPostbuildAction.class);
            if(index < 0 || index >= badgeActions.size()) {
                listener.error("Invalid badge index: " + index + ". Allowed values: 0 .. " + (badgeActions.size()-1));
            } else {
                JythonPostbuildAction action = badgeActions.get(index);
                actions.remove(action);
            }
        }

        public JythonPostbuildSummaryAction createSummary(String icon) {
            JythonPostbuildSummaryAction action = new JythonPostbuildSummaryAction(icon);
            build.getActions().add(action);
            return action;
        }
        public void removeSummaries() {
            List<Action> actions = build.getActions();
            List<JythonPostbuildSummaryAction> summaryActions = build.getActions(JythonPostbuildSummaryAction.class);
            for(JythonPostbuildSummaryAction action : summaryActions) {
                actions.remove(action);
            }
        }
        public void removeSummary(int index) {
            List<Action> actions = build.getActions();
            List<JythonPostbuildSummaryAction> summaryActions = build.getActions(JythonPostbuildSummaryAction.class);
            if(index < 0 || index >= summaryActions.size()) {
                listener.error("Invalid summary index: " + index + ". Allowed values: 0 .. " + (summaryActions.size()-1));
            } else {
                JythonPostbuildSummaryAction action = summaryActions.get(index);
                actions.remove(action);
            }
        }

        public void buildUnstable() {
            build.setResult(Result.UNSTABLE);
        }

        public void buildFailure() {
            build.setResult(Result.FAILURE);
        }

        public void buildSuccess() {
            build.setResult(Result.SUCCESS);
        }

        public void buildAborted() {
            build.setResult(Result.ABORTED);
        }

        public void buildNotBuilt() {
            build.setResult(Result.NOT_BUILT);
        }

        public void buildScriptFailed(Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            boolean isError = scriptFailureResult.isWorseThan(Result.UNSTABLE);
            String icon = isError ? "error" : "warning";
            JythonPostbuildSummaryAction summary = createSummary(icon + ".gif");
            summary.appendText("<b><font color=\"red\">Jython script failed:</font></b><br><pre>", false);
            summary.appendText(writer.toString(), true);
            summary.appendText("</pre>", false);

            addShortText("Jython", "black", isError ? "#FFE0E0" : "#FFFFC0", "1px", isError ? "#E08080" : "#C0C080");

            Result result = build.getResult();
            if(result.isBetterThan(scriptFailureResult)) {
                build.setResult(scriptFailureResult);
            }
        }

        public boolean logContains(String regexp) {
            return contains(build.getLogFile(), regexp);
        }

        public boolean contains(File f, String regexp) {
            Matcher matcher = getMatcher(f, regexp);
            return (matcher != null) && matcher.matches();
        }

        public Matcher getLogMatcher(String regexp) {
            return getMatcher(build.getLogFile(), regexp);
        }

        public Matcher getMatcher(File f, String regexp) {
            LOGGER.fine("Searching for '" + regexp + "' in '" + f + "'.");
            Matcher matcher = null;
            BufferedReader reader = null;
            try {
                Pattern pattern = compilePattern(regexp);
                // Assume default encoding and text files
                String line;
                reader = new BufferedReader(new FileReader(f));
                while ((line = reader.readLine()) != null) {
                    Matcher m = pattern.matcher(line);
                    if (m.matches()) {
                        matcher = m;
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(listener.error("Jython Postbuild: getMatcher(\"" + f + "\", \"" + regexp + "\") failed."));
                buildScriptFailed(e);
            } finally {
                IOUtils.closeQuietly(reader);
            }
            return matcher;
        }

        private Pattern compilePattern(String regexp) throws AbortException {
            Pattern pattern;
            try {
                pattern = Pattern.compile(regexp);
            } catch (PatternSyntaxException e) {
                listener.getLogger().println("Jython Postbuild: Unable to compile regular expression '" + regexp + "'");
                throw new AbortException();
            }
            return pattern;
        }

        /**
         * Test whether the current build is specified type.
         *
         * @param buildClass
         * @return true if the current build is an instance of buildClass
         */
        public boolean buildIsA(Class<? extends AbstractBuild<?, ?>> buildClass) {
            return buildClass.isInstance(getBuild());
        }
    }

    @DataBoundConstructor
    public JythonPostbuildRecorder(String script, int behavior, boolean runForMatrixParent) {
        this.script = script;
        this.behavior = behavior;
        this.runForMatrixParent = runForMatrixParent;
        LOGGER.fine("JythonPostbuildRecorder created with jythonScript:\n" + script);
        LOGGER.fine("JythonPostbuildRecorder behavior:" + behavior);
    }

    private Object readResolve() {
        return this;
    }

    @Override
    public final Action getProjectAction(final AbstractProject<?, ?> project) {
        return null;
    }

    @Override
    public JythonPostbuildDescriptor getDescriptor() {
        return (JythonPostbuildDescriptor)super.getDescriptor();
    }

    @Override
    public final boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        LOGGER.fine("perform() called for script");
        LOGGER.fine("behavior: " + behavior);
        Result scriptFailureResult = Result.SUCCESS;
        switch(behavior) {
            case 0: scriptFailureResult = Result.SUCCESS; break;
            case 1: scriptFailureResult = Result.UNSTABLE; break;
            case 2: scriptFailureResult = Result.FAILURE; break;
        }
        BadgeManager badgeManager = new BadgeManager(build, listener, scriptFailureResult);
        ClassLoader cl = Jenkins.getInstance().getPluginManager().uberClassLoader;
        //Binding binding = new Binding();
        //binding.setVariable("manager", badgeManager);
        try {
            //script.evaluate(cl, binding);
            PythonInterpreter.initialize(System.getProperties(), System.getProperties(), new String[0]);
            PythonInterpreter interp = new PythonInterpreter();
            interp.set("manager", badgeManager);
            interp.set("self", this);
            interp.exec(script);
        } catch (Exception e) {
            // TODO could print more refined errors for UnapprovedUsageException and/or RejectedAccessException:
            e.printStackTrace(listener.error("Failed to evaluate jython script."));
            badgeManager.buildScriptFailed(e);
        }
        for(AbstractBuild<?, ?> b : badgeManager.builds) {
            b.save();
        }
        return build.getResult().isBetterThan(Result.FAILURE);
    }

    public final BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getScript() {
        return script;
    }

    public int getBehavior() {
        return behavior;
    }

    public boolean isRunForMatrixParent() {
        return runForMatrixParent;
    }

    /**
     * @param build
     * @param launcher
     * @param listener
     * @return
     * @see hudson.matrix.MatrixAggregatable#createAggregator(hudson.matrix.MatrixBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    public MatrixAggregator createAggregator(final MatrixBuild build, final Launcher launcher, final BuildListener listener) {
        if (!isRunForMatrixParent()) {
            return null;
        }

        return new MatrixAggregator(build, launcher, listener) {
            /**
             * Called when all child builds are finished.
             *
             * @return
             * @throws InterruptedException
             * @throws IOException
             * @see hudson.matrix.MatrixAggregator#endBuild()
             */
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return perform(build, launcher, listener);
            }
        };
    }
}
