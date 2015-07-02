package hudson.plugins.analysis.core; // NOPMD

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;

import jenkins.tasks.SimpleBuildStep;

import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.analysis.util.EncodingValidator;
import hudson.plugins.analysis.util.Files;
import hudson.plugins.analysis.util.LoggerFactory;
import hudson.plugins.analysis.util.PluginLogger;
import hudson.plugins.analysis.util.model.FileAnnotation;
import hudson.plugins.analysis.util.model.Priority;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Recorder;

/**
 * A base class for publishers with the following two characteristics:
 * <ul>
 * <li>It provides a unstable threshold, that could be enabled and set in the
 * configuration screen. If the number of annotations in a build exceeds this
 * value then the build is considered as {@link Result#UNSTABLE UNSTABLE}.</li>
 * <li>It provides thresholds for the build health, that could be adjusted in
 * the configuration screen. These values are used by the
 * {@link HealthReportBuilder} to compute the health and the health trend graph.
 * </li>
 * </ul>
 *
 * @author Ulli Hafner
 */
// CHECKSTYLE:COUPLING-OFF
@SuppressWarnings("PMD.TooManyFields")
public abstract class HealthAwareRecorder extends Recorder implements HealthDescriptor, MatrixAggregatable, SimpleBuildStep {
    private static final long serialVersionUID = 8892994325541840827L;

    /** Default threshold priority limit. */
    private static final String DEFAULT_PRIORITY_THRESHOLD_LIMIT = "low";
    /** Report health as 100% when the number of warnings is less than this value. */
    private String healthy = "10";
    /** Report health as 0% when the number of warnings is greater than this value. */
    private String unHealthy = "20";
    /** Determines which warning priorities should be considered when evaluating the build health. */
    private String thresholdLimit = Priority.HIGH.getLocalizedString();

    /** The name of the plug-in. */
    private final String pluginName;
    /** The default encoding to be used when reading and parsing files. */
    private String defaultEncoding = StringUtils.EMPTY;
    /**
     * Determines whether the plug-in should run for failed builds, too.
     *
     * @since 1.6
     */
    private boolean canRunOnFailed = true;
    /** Determines whether the previous build should always be used as the
     * reference build.
     * @since 1.66
     */
    private boolean usePreviousBuildAsReference = false;
    /**
     * Determines whether only stable builds should be used as reference builds
     * or not.
     *
     * @since 1.48
     */
    private boolean useStableBuildAsReference = true;
    /**
     * Determines whether the absolute annotations delta or the actual
     * annotations set difference should be used to evaluate the build
     * stability.
     *
     * @since 1.4
     */
    private boolean useDeltaValues = false;
    /**
     * Thresholds for build status unstable and failed, resp. and priorities
     * all, high, normal, and low, resp.
     *
     * @since 1.14
     */
    private Thresholds thresholds = new Thresholds();
    /**
     * Determines whether module names should be derived from Maven POM or Ant
     * build files.
     *
     * @since 1.19
     */
    private boolean shouldDetectModules = true;
    /**
     * Determines whether new warnings should be computed (with respect to
     * baseline).
     *
     * @since 1.34
     */
    private boolean dontComputeNew = false;
    /**
     * Determines whether relative paths in warnings should be resolved using a
     * time expensive operation that scans the whole workspace for matching
     * files.
     *
     * @since 1.43
     */
    private boolean doNotResolveRelativePaths = false;

    /**
     * Creates a new instance of {@link HealthAwareRecorder}.
     *
     * @param healthy
     *            Report health as 100% when the number of open tasks is less
     *            than this value
     * @param unHealthy
     *            Report health as 0% when the number of open tasks is greater
     *            than this value
     * @param thresholdLimit
     *            determines which warning priorities should be considered when
     *            evaluating the build stability and health
     * @param defaultEncoding
     *            the default encoding to be used when reading and parsing files
     * @param useDeltaValues
     *            determines whether the absolute annotations delta or the
     *            actual annotations set difference should be used to evaluate
     *            the build stability
     * @param unstableTotalAll
     *            annotation threshold
     * @param unstableTotalHigh
     *            annotation threshold
     * @param unstableTotalNormal
     *            annotation threshold
     * @param unstableTotalLow
     *            annotation threshold
     * @param unstableNewAll
     *            annotation threshold
     * @param unstableNewHigh
     *            annotation threshold
     * @param unstableNewNormal
     *            annotation threshold
     * @param unstableNewLow
     *            annotation threshold
     * @param failedTotalAll
     *            annotation threshold
     * @param failedTotalHigh
     *            annotation threshold
     * @param failedTotalNormal
     *            annotation threshold
     * @param failedTotalLow
     *            annotation threshold
     * @param failedNewAll
     *            annotation threshold
     * @param failedNewHigh
     *            annotation threshold
     * @param failedNewNormal
     *            annotation threshold
     * @param failedNewLow
     *            annotation threshold
     * @param canRunOnFailed
     *            determines whether the plug-in can run for failed builds, too
     * @param usePreviousBuildAsReference
     *            determine if the previous build should always be used as the
     *            reference build, no matter its overall result.
     * @param useStableBuildAsReference
     *            determines whether only stable builds should be used as
     *            reference builds or not
     * @param shouldDetectModules
     *            determines whether module names should be derived from Maven
     *            POM or Ant build files
     * @param canComputeNew
     *            determines whether new warnings should be computed (with
     *            respect to baseline)
     * @param canResolveRelativePaths
     *            determines whether relative paths in warnings should be
     *            resolved using a time expensive operation that scans the whole
     *            workspace for matching files.
     * @param pluginName
     *            the name of the plug-in
     */
    // CHECKSTYLE:OFF
    @SuppressWarnings("PMD")
    @Deprecated
    public HealthAwareRecorder(final String healthy, final String unHealthy,
            final String thresholdLimit, final String defaultEncoding,
            final boolean useDeltaValues, final String unstableTotalAll,
            final String unstableTotalHigh, final String unstableTotalNormal,
            final String unstableTotalLow, final String unstableNewAll,
            final String unstableNewHigh, final String unstableNewNormal,
            final String unstableNewLow, final String failedTotalAll, final String failedTotalHigh,
            final String failedTotalNormal, final String failedTotalLow, final String failedNewAll,
            final String failedNewHigh, final String failedNewNormal, final String failedNewLow,
            final boolean canRunOnFailed, final boolean usePreviousBuildAsReference,
            final boolean useStableBuildAsReference,
            final boolean shouldDetectModules, final boolean canComputeNew,
            final boolean canResolveRelativePaths, final String pluginName) {
        super();
        this.healthy = healthy;
        this.unHealthy = unHealthy;
        this.thresholdLimit = StringUtils.defaultIfEmpty(thresholdLimit, DEFAULT_PRIORITY_THRESHOLD_LIMIT);
        this.defaultEncoding = defaultEncoding;
        this.useDeltaValues = useDeltaValues;

        doNotResolveRelativePaths = !canResolveRelativePaths;
        dontComputeNew = !canComputeNew;

        thresholds.unstableTotalAll = unstableTotalAll;
        thresholds.unstableTotalHigh = unstableTotalHigh;
        thresholds.unstableTotalNormal = unstableTotalNormal;
        thresholds.unstableTotalLow = unstableTotalLow;
        thresholds.unstableNewAll = unstableNewAll;
        thresholds.unstableNewHigh = unstableNewHigh;
        thresholds.unstableNewNormal = unstableNewNormal;
        thresholds.unstableNewLow = unstableNewLow;
        thresholds.failedTotalAll = failedTotalAll;
        thresholds.failedTotalHigh = failedTotalHigh;
        thresholds.failedTotalNormal = failedTotalNormal;
        thresholds.failedTotalLow = failedTotalLow;
        thresholds.failedNewAll = failedNewAll;
        thresholds.failedNewHigh = failedNewHigh;
        thresholds.failedNewNormal = failedNewNormal;
        thresholds.failedNewLow = failedNewLow;

        this.canRunOnFailed = canRunOnFailed;
        this.usePreviousBuildAsReference = usePreviousBuildAsReference;
        this.useStableBuildAsReference = useStableBuildAsReference;
        this.shouldDetectModules = shouldDetectModules;
        this.pluginName = "[" + pluginName + "] ";
    }

    @DataBoundSetter
    public void setHealthy(final String healthy) {
        this.healthy = healthy;
    }

    @DataBoundSetter
    public void setUnHealthy(final String unHealthy) {
        this.unHealthy = unHealthy;
    }

    @DataBoundSetter
    public void setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    @DataBoundSetter
    public void setThresholdLimit(final String thresholdLimit) {
        this.thresholdLimit = thresholdLimit;
    }

    @DataBoundSetter
    public void setCanRunOnFailed(final boolean canRunOnFailed) {
        this.canRunOnFailed = canRunOnFailed;
    }

    @DataBoundSetter
    public void setUsePreviousBuildAsReference(final boolean usePreviousBuildAsReference) {
        this.usePreviousBuildAsReference = usePreviousBuildAsReference;
    }

    @DataBoundSetter
    public void setUseStableBuildAsReference(final boolean useStableBuildAsReference) {
        this.useStableBuildAsReference = useStableBuildAsReference;
    }

    @DataBoundSetter
    public void setUseDeltaValues(final boolean useDeltaValues) {
        this.useDeltaValues = useDeltaValues;
    }

    @DataBoundSetter
    public void setShouldDetectModules(final boolean shouldDetectModules) {
        this.shouldDetectModules = shouldDetectModules;
    }

    @DataBoundSetter
    public void setCanComputeNew(final boolean canComputeNew) {
        this.dontComputeNew = !canComputeNew;
    }

    @DataBoundSetter
    public void setCanResolveRelativePaths(final boolean canResolveRelativePaths) {
        this.doNotResolveRelativePaths = !canResolveRelativePaths;
    }

    @DataBoundSetter
    public void setUnstableTotalAll(final String unstableTotalAll) {
        thresholds.unstableTotalAll = unstableTotalAll;
    }

    @DataBoundSetter
    public void setUnstableTotalHigh(final String unstableTotalHigh) {
        thresholds.unstableTotalHigh = unstableTotalHigh;
    }

    @DataBoundSetter
    public void setUnstableTotalNormal(final String unstableTotalNormal) {
        thresholds.unstableTotalNormal = unstableTotalNormal;
    }

    @DataBoundSetter
    public void setUnstableTotalLow(final String unstableTotalLow) {
        thresholds.unstableTotalLow = unstableTotalLow;
    }

    @DataBoundSetter
    public void setUnstableNewAll(final String unstableNewAll) {
        thresholds.unstableNewAll = unstableNewAll;
    }

    @DataBoundSetter
    public void setUnstableNewHigh(final String unstableNewHigh) {
        thresholds.unstableNewHigh = unstableNewHigh;
    }

    @DataBoundSetter
    public void setUnstableNewNormal(final String unstableNewNormal) {
        thresholds.unstableNewNormal = unstableNewNormal;
    }

    @DataBoundSetter
    public void setUnstableNewLow(final String unstableNewLow) {
        thresholds.unstableNewLow = unstableNewLow;
    }

    @DataBoundSetter
    public void setFailedTotalAll(final String failedTotalAll) {
        thresholds.failedTotalAll = failedTotalAll;
    }

    @DataBoundSetter
    public void setFailedTotalHigh(final String failedTotalHigh) {
        thresholds.failedTotalHigh = failedTotalHigh;
    }

    @DataBoundSetter
    public void setFailedTotalNormal(final String failedTotalNormal) {
        thresholds.failedTotalNormal = failedTotalNormal;
    }

    @DataBoundSetter
    public void setFailedTotalLow(final String failedTotalLow) {
        thresholds.failedTotalLow = failedTotalLow;
    }

    @DataBoundSetter
    public void setFailedNewAll(final String failedNewAll) {
        thresholds.failedNewAll = failedNewAll;
    }

    @DataBoundSetter
    public void setFailedNewHigh(final String failedNewHigh) {
        thresholds.failedNewHigh = failedNewHigh;
    }

    @DataBoundSetter
    public void setFailedNewNormal(final String failedNewNormal) {
        thresholds.failedNewNormal = failedNewNormal;
    }

    @DataBoundSetter
    public void setFailedNewLow(final String failedNewLow) {
        thresholds.failedNewLow = failedNewLow;
    }
    // CHECKSTYLE:ON

    public HealthAwareRecorder(final String pluginName) {
        this.pluginName = pluginName;
    }

    /**
     * Returns whether relative paths in warnings should be resolved using a
     * time expensive operation that scans the whole workspace for matching
     * files.
     *
     * @return <code>true</code> if relative paths can be resolved,
     *         <code>false</code> otherwise
     */
    public boolean getCanResolveRelativePaths() {
        return !doNotResolveRelativePaths;
    }

    /**
     * Returns whether relative paths in warnings should be resolved using a
     * time expensive operation that scans the whole workspace for matching
     * files.
     *
     * @return <code>true</code> if relative paths can be resolved,
     *         <code>false</code> otherwise
     */
    public boolean canResolveRelativePaths() {
        return getCanResolveRelativePaths();
    }

    /**
     * Returns whether there is a health threshold enabled.
     *
     * @return <code>true</code> if at least one threshold is enabled,
     *         <code>false</code> otherwise
     */
    protected boolean isThresholdEnabled() {
        return new NullHealthDescriptor(this).isThresholdEnabled();
    }

    /**
     * Determines if the previous build should always be used as the reference build, no matter its overall result.
     *
     * @return <code>true</code> the previous build should always be used
     */
    public boolean usePreviousBuildAsReference() {
        return getUsePreviousBuildAsReference();
    }

    /**
     * Determines if the previous build should always be used as the reference build, no matter its overall result.
     *
     * @return <code>true</code> the previous build should always be used
     */
    public boolean getUsePreviousBuildAsReference() {
        return usePreviousBuildAsReference;
    }

    /**
     * Determines whether only stable builds should be used as reference builds
     * or not.
     *
     * @return <code>true</code> if only stable builds should be used
     */
    public boolean getUseStableBuildAsReference() {
        return useStableBuildAsReference;
    }

    /**
     * Determines whether only stable builds should be used as reference builds
     * or not.
     *
     * @return <code>true</code> if only stable builds should be used
     */
    public boolean useOnlyStableBuildsAsReference() {
        return getUseStableBuildAsReference();
    }

    /**
     * Initializes new fields that are not serialized yet.
     *
     * @return the object
     */
    protected Object readResolve() {
        if (thresholdLimit == null) {
            thresholdLimit = DEFAULT_PRIORITY_THRESHOLD_LIMIT;
        }
        if (thresholds == null) {
            thresholds = new Thresholds();

            if (threshold != null) {
                thresholds.unstableTotalAll = threshold;
                threshold = null; // NOPMD
            }
            if (newThreshold != null) {
                thresholds.unstableNewAll = newThreshold;
                newThreshold = null; // NOPMD
            }
            if (failureThreshold != null) {
                thresholds.failedTotalAll = failureThreshold;
                failureThreshold = null; // NOPMD
            }
            if (newFailureThreshold != null) {
                thresholds.failedNewAll = newFailureThreshold;
                newFailureThreshold = null; // NOPMD
            }
        }
        return this;
    }

    @Override
    public final void perform(final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener) throws InterruptedException, IOException {
        PluginLogger logger = new LoggerFactory().createLogger(listener.getLogger(), pluginName);
        if (canContinue(run.getResult())) {
            perform(run, workspace, launcher, listener, logger);
        }
        else {
            logger.log("Skipping publisher since build result is " + run.getResult());
        }
    }

    protected abstract void perform(final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener, final PluginLogger logger) throws InterruptedException, IOException;

    @Override
    public PluginDescriptor getDescriptor() {
        return (PluginDescriptor)super.getDescriptor();
    }

    /**
     * Copies all files with annotations from the workspace to the build folder.
     *
     * @param rootDir
     *            directory to store the copied files in
     * @param channel
     *            channel to get the files from
     * @param annotations
     *            annotations determining the actual files to copy
     * @throws IOException
     *             if the files could not be written
     * @throws FileNotFoundException
     *             if the files could not be written
     * @throws InterruptedException
     *             if the user cancels the processing
     */
    protected void copyFilesWithAnnotationsToBuildFolder(final File rootDir,
            final VirtualChannel channel, final Collection<FileAnnotation> annotations)
            throws IOException, FileNotFoundException, InterruptedException {
        new Files().copyFilesWithAnnotationsToBuildFolder(channel, new FilePath(rootDir), annotations,
                EncodingValidator.getEncoding(getDefaultEncoding()));
    }

    /**
     * Returns whether new warnings should be computed (with respect to
     * baseline).
     *
     * @return <code>true</code> if new warnings should be computed (with
     *         respect to baseline), <code>false</code> otherwise
     */
    public boolean getCanComputeNew() {
        return canComputeNew();
    }

    /**
     * Returns whether new warnings should be computed (with respect to
     * baseline).
     *
     * @return <code>true</code> if new warnings should be computed (with
     *         respect to baseline), <code>false</code> otherwise
     */
    public boolean canComputeNew() {
        return !dontComputeNew;
    }

    /**
     * Returns whether this plug-in can run for failed builds, too.
     *
     * @return <code>true</code> if this plug-in can run for failed builds,
     *         <code>false</code> otherwise
     */
    public boolean getCanRunOnFailed() {
        return canRunOnFailed;
    }

    /**
     * Returns whether module names should be derived from Maven POM or Ant
     * build files.
     *
     * @return the can run on failed
     */
    public boolean getShouldDetectModules() {
        return shouldDetectModules;
    }

    /**
     * Returns whether module names should be derived from Maven POM or Ant
     * build files.
     *
     * @return the can run on failed
     */
    public boolean shouldDetectModules() {
        return shouldDetectModules;
    }

    /**
     * Returns whether this publisher can continue processing. This default
     * implementation returns <code>true</code> if the property
     * <code>canRunOnFailed</code> is set or if the build is not aborted or
     * failed.
     *
     * @param result
     *            build result
     * @return <code>true</code> if the build can continue
     */
    protected boolean canContinue(final Result result) {
        if (canRunOnFailed) {
            return result != Result.ABORTED;
        }
        else {
            return result != Result.ABORTED && result != Result.FAILURE;
        }
    }

    @Override
    public Thresholds getThresholds() {
        return thresholds;
    }

    /**
     * Returns whether absolute annotations delta or the actual annotations set
     * difference should be used to evaluate the build stability.
     *
     * @return <code>true</code> if the annotation count should be used,
     *         <code>false</code> if the actual (set) difference should be
     *         computed
     */
    public boolean getUseDeltaValues() {
        return useDeltaValues;
    }

    /**
     * Returns the healthy threshold, i.e. when health is reported as 100%.
     *
     * @return the 100% healthiness
     */
    @Override
    public String getHealthy() {
        return healthy;
    }

    /**
     * Returns the unhealthy threshold, i.e. when health is reported as 0%.
     *
     * @return the 0% unhealthiness
     */
    @Override
    public String getUnHealthy() {
        return unHealthy;
    }

    /**
     * Returns the defined default encoding.
     *
     * @return the default encoding
     */
    @CheckForNull
    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    /**
     * Returns whether the current build uses maven.
     *
     * @param build
     *            the current build
     * @return <code>true</code> if the current build uses maven,
     *         <code>false</code> otherwise
     */
    protected boolean isMavenBuild(final Run<?, ?> build) {
        if (build.getParent() instanceof Project) {
            Project<?, ?> project = (Project<?, ?>)build.getParent();
            for (Builder builder : project.getBuilders()) {
                if (builder instanceof Maven) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the current build uses ant.
     *
     * @param build
     *            the current build
     * @return <code>true</code> if the current build uses ant,
     *         <code>false</code> otherwise
     */
    protected boolean isAntBuild(final AbstractBuild<?, ?> build) {
        try {
            return AntBuilderCheck.isAntBuild(build);
        }
        catch (Throwable exception) { // NOPMD NOCHECKSTYLE
            return false; // fallback if ant is not installed
        }
    }

    @Override
    public Priority getMinimumPriority() {
        return Priority.valueOf(StringUtils.upperCase(getThresholdLimit()));
    }

    /**
     * Returns the threshold limit.
     *
     * @return the threshold limit
     */
    public String getThresholdLimit() {
        return thresholdLimit;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return canComputeNew() ? BuildStepMonitor.STEP : BuildStepMonitor.NONE;
    }

    // CHECKSTYLE:OFF
    /** Backward compatibility. @deprecated */
    @Deprecated
    private transient String threshold;
    /** Backward compatibility. @deprecated */
    @Deprecated
    private transient String newThreshold;
    /** Backward compatibility. @deprecated */
    @Deprecated
    private transient String failureThreshold;
    /** Backward compatibility. @deprecated */
    @Deprecated
    private transient String newFailureThreshold;
    /** Backward compatibility. @deprecated */
    @SuppressWarnings("unused")
    @Deprecated
    private transient boolean thresholdEnabled;
    /** Backward compatibility. @deprecated */
    @SuppressWarnings("unused")
    @Deprecated
    private transient int minimumAnnotations;
    /** Backward compatibility. @deprecated */
    @SuppressWarnings("unused")
    @Deprecated
    private transient int healthyAnnotations;
    /** Backward compatibility. @deprecated */
    @SuppressWarnings("unused")
    @Deprecated
    private transient int unHealthyAnnotations;
    /** Backward compatibility. @deprecated */
    @SuppressWarnings("unused")
    @Deprecated
    private transient boolean healthyReportEnabled;
    /** Backward compatibility. @deprecated */
    @SuppressWarnings("unused")
    @Deprecated
    private transient String height;

    /** Backwards compatibility.
     * @deprecated
     */
    @Deprecated
    @SuppressWarnings("PMD")
    public HealthAwareRecorder(final String healthy, final String unHealthy,
            final String thresholdLimit, final String defaultEncoding,
            final boolean useDeltaValues, final String unstableTotalAll,
            final String unstableTotalHigh, final String unstableTotalNormal,
            final String unstableTotalLow, final String unstableNewAll,
            final String unstableNewHigh, final String unstableNewNormal,
            final String unstableNewLow, final String failedTotalAll,
            final String failedTotalHigh, final String failedTotalNormal,
            final String failedTotalLow, final String failedNewAll,
            final String failedNewHigh, final String failedNewNormal,
            final String failedNewLow, final boolean canRunOnFailed,
            final boolean useStableBuildAsReference,
            final boolean shouldDetectModules, final boolean canComputeNew,
            final boolean canResolveRelativePaths, final String pluginName) {
        this(healthy, unHealthy, thresholdLimit, defaultEncoding,
                useDeltaValues, unstableTotalAll, unstableTotalHigh,
                unstableTotalNormal, unstableTotalLow, unstableNewAll,
                unstableNewHigh, unstableNewNormal, unstableNewLow,
                failedTotalAll, failedTotalHigh, failedTotalNormal,
                failedTotalLow, failedNewAll, failedNewHigh, failedNewNormal,
                failedNewLow, canRunOnFailed, false, useStableBuildAsReference,
                shouldDetectModules, canComputeNew, canResolveRelativePaths,
                pluginName);
    }

    /** Backward compatibility. @deprecated */
    @SuppressWarnings({"PMD","javadoc"})
    @Deprecated
    public HealthAwareRecorder(final String threshold, final String newThreshold,
            final String failureThreshold, final String newFailureThreshold, final String healthy,
            final String unHealthy, final String thresholdLimit, final String defaultEncoding,
            final boolean useDeltaValues, final boolean canRunOnFailed, final String pluginName) {
        super();

        thresholds.unstableTotalAll = threshold;
        thresholds.unstableNewAll = newThreshold;
        thresholds.failedTotalAll = failureThreshold;
        thresholds.failedNewAll = newFailureThreshold;
        doNotResolveRelativePaths = false;

        this.healthy = healthy;
        this.unHealthy = unHealthy;
        this.thresholdLimit = thresholdLimit;
        this.defaultEncoding = defaultEncoding;
        this.useDeltaValues = useDeltaValues;
        this.canRunOnFailed = canRunOnFailed;
        this.usePreviousBuildAsReference = false;
        useStableBuildAsReference = false;
        dontComputeNew = false;
        shouldDetectModules = false;
        this.pluginName = "[" + pluginName + "] ";
    }

    /** Backward compatibility. @deprecated */
    @SuppressWarnings({"PMD","javadoc"})
    @Deprecated
    public HealthAwareRecorder(final String healthy, final String unHealthy,
            final String thresholdLimit, final String defaultEncoding,
            final boolean useDeltaValues, final String unstableTotalAll,
            final String unstableTotalHigh, final String unstableTotalNormal,
            final String unstableTotalLow, final String unstableNewAll,
            final String unstableNewHigh, final String unstableNewNormal,
            final String unstableNewLow, final String failedTotalAll, final String failedTotalHigh,
            final String failedTotalNormal, final String failedTotalLow, final String failedNewAll,
            final String failedNewHigh, final String failedNewNormal, final String failedNewLow,
            final boolean canRunOnFailed,
            final boolean shouldDetectModules, final boolean canComputeNew,
            final boolean canResolveRelativePaths, final String pluginName) {
        this(healthy, unHealthy, thresholdLimit, defaultEncoding, useDeltaValues,
                unstableTotalAll, unstableTotalHigh, unstableTotalNormal, unstableTotalLow,
                unstableNewAll, unstableNewHigh, unstableNewNormal, unstableNewLow,
                failedTotalAll, failedTotalHigh, failedTotalNormal, failedTotalLow,
                failedNewAll, failedNewHigh, failedNewNormal, failedNewLow,
                canRunOnFailed, false, shouldDetectModules, canComputeNew, canResolveRelativePaths, pluginName);
    }
    // CHECKSTYLE:OFF
}
