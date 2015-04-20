package org.jenkinsci.plugins.ghprb;

import antlr.ANTLRException;

import com.coravy.hudson.plugins.github.GithubProjectProperty;
import com.google.common.annotations.VisibleForTesting;

import hudson.Extension;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.RevisionParameterAction;
import hudson.plugins.git.util.BuildData;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.kohsuke.github.GHAuthorization;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbTrigger extends Trigger<AbstractProject<?, ?>> {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private static final Logger logger = Logger.getLogger(GhprbTrigger.class.getName());
    private final String adminlist;
    private GhprbGithubCredentials credentials;
    private final Boolean allowMembersOfWhitelistedOrgsAsAdmin;
    private final String orgslist;
    private final String cron;
    private final String triggerPhrase;
    private final Boolean onlyTriggerPhrase;
    private final Boolean useGitHubHooks;
    private final Boolean permitAll;
    private final String commentFilePath;
    private String whitelist;
    private Boolean autoCloseFailedPullRequests;
    private Boolean displayBuildErrorsOnDownstreamBuilds;
    private List<GhprbBranch> whiteListTargetBranches;
    private String msgSuccess;
    private String msgFailure;
    private String commitStatusContext;
    private transient Ghprb helper;
    private String project;

    public GhprbTrigger(String adminlist, String whitelist, String orgslist, String cron, String triggerPhrase, Boolean onlyTriggerPhrase, Boolean useGitHubHooks, Boolean permitAll,
            Boolean autoCloseFailedPullRequests, Boolean displayBuildErrorsOnDownstreamBuilds, String commentFilePath, List<GhprbBranch> whiteListTargetBranches,
            Boolean allowMembersOfWhitelistedOrgsAsAdmin, String msgSuccess, String msgFailure, String commitStatusContext) throws ANTLRException {
        this(adminlist, whitelist, orgslist, cron, triggerPhrase, onlyTriggerPhrase, useGitHubHooks, permitAll, autoCloseFailedPullRequests, displayBuildErrorsOnDownstreamBuilds, commentFilePath,
                whiteListTargetBranches, allowMembersOfWhitelistedOrgsAsAdmin, msgSuccess, msgFailure, commitStatusContext, "");
    }

    @DataBoundConstructor
    public GhprbTrigger(String adminlist, String whitelist, String orgslist, String cron, String triggerPhrase, Boolean onlyTriggerPhrase, Boolean useGitHubHooks, Boolean permitAll,
            Boolean autoCloseFailedPullRequests, Boolean displayBuildErrorsOnDownstreamBuilds, String commentFilePath, List<GhprbBranch> whiteListTargetBranches,
            Boolean allowMembersOfWhitelistedOrgsAsAdmin, String msgSuccess, String msgFailure, String commitStatusContext, String credentials) throws ANTLRException {
        super(cron);
        this.adminlist = adminlist;
        this.whitelist = whitelist;
        this.orgslist = orgslist;
        this.cron = cron;
        this.triggerPhrase = triggerPhrase;
        this.onlyTriggerPhrase = onlyTriggerPhrase;
        this.useGitHubHooks = useGitHubHooks;
        this.permitAll = permitAll;
        this.autoCloseFailedPullRequests = autoCloseFailedPullRequests;
        this.displayBuildErrorsOnDownstreamBuilds = displayBuildErrorsOnDownstreamBuilds;
        this.whiteListTargetBranches = whiteListTargetBranches;
        this.commitStatusContext = commitStatusContext;
        this.commentFilePath = commentFilePath;
        this.allowMembersOfWhitelistedOrgsAsAdmin = allowMembersOfWhitelistedOrgsAsAdmin;
        this.msgSuccess = msgSuccess;
        this.msgFailure = msgFailure;
       
        logger.log(Level.INFO, "@DataBoundConstructor: " + ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE));
        if (credentials == null || credentials.isEmpty()) {
            this.credentials = DESCRIPTOR.getDefaultCredentials();
            logger.log(Level.INFO, "credentials is empty. default is " + ReflectionToStringBuilder.toString(credentials, ToStringStyle.MULTI_LINE_STYLE));
        } else {
        	this.credentials = DESCRIPTOR.getCredentials(credentials);
            logger.log(Level.INFO, "credentials is " + ReflectionToStringBuilder.toString(credentials, ToStringStyle.MULTI_LINE_STYLE));
        }
    }

    public GhprbTrigger(String adminlist, String whitelist, String orgslist, String cron, String triggerPhrase, Boolean onlyTriggerPhrase, Boolean useGitHubHooks, Boolean permitAll,
            Boolean autoCloseFailedPullRequests, Boolean displayBuildErrorsOnDownstreamBuilds, String commentFilePath, List<GhprbBranch> whiteListTargetBranches,
            Boolean allowMembersOfWhitelistedOrgsAsAdmin, String msgSuccess, String msgFailure, String commitStatusContext, GhprbGithubCredentials credentials) throws ANTLRException {
        super(cron);
        this.adminlist = adminlist;
        this.whitelist = whitelist;
        this.orgslist = orgslist;
        this.cron = cron;
        this.triggerPhrase = triggerPhrase;
        this.onlyTriggerPhrase = onlyTriggerPhrase;
        this.useGitHubHooks = useGitHubHooks;
        this.permitAll = permitAll;
        this.autoCloseFailedPullRequests = autoCloseFailedPullRequests;
        this.displayBuildErrorsOnDownstreamBuilds = displayBuildErrorsOnDownstreamBuilds;
        this.whiteListTargetBranches = whiteListTargetBranches;
        this.commitStatusContext = commitStatusContext;
        this.commentFilePath = commentFilePath;
        this.allowMembersOfWhitelistedOrgsAsAdmin = allowMembersOfWhitelistedOrgsAsAdmin;
        this.msgSuccess = msgSuccess;
        this.msgFailure = msgFailure;
        if (credentials == null) {
            this.credentials = DESCRIPTOR.getDefaultCredentials();
        } else {
            this.credentials = credentials;
        }
    }

    public static GhprbTrigger extractTrigger(AbstractProject<?, ?> p) {
        GhprbTrigger trigger = p.getTrigger(GhprbTrigger.class);
        if (trigger == null || (!(trigger instanceof GhprbTrigger))) {
            return null;
        }
        return (GhprbTrigger) trigger;
    }

    public static DescriptorImpl getDscp() {
        return DESCRIPTOR;
    }

    public GhprbGithubCredentials getCredentials() {
    	if (this.credentials == null) {
    		 this.credentials = DESCRIPTOR.getDefaultCredentials();
    	}
        DESCRIPTOR.saveSetup();
        return credentials;
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        this.project = project.getName();
        if (project.getProperty(GithubProjectProperty.class) == null) {
            logger.log(Level.INFO, "GitHub project not set up, cannot start ghprb trigger for job " + this.project);
            return;
        }
        try {
            helper = createGhprb(project);
        } catch (IllegalStateException ex) {
            logger.log(Level.INFO, "Can't start ghprb trigger", ex);
            return;
        }

        logger.log(Level.INFO, "Starting the ghprb trigger for the {0} job; newInstance is {1}", new String[] { this.project, String.valueOf(newInstance) });
        logger.log(Level.INFO, "credential is " + ReflectionToStringBuilder.toString(credentials, ToStringStyle.MULTI_LINE_STYLE));
        super.start(project, newInstance);
        helper.init();
    }

    Ghprb createGhprb(AbstractProject<?, ?> project) {
        return new Ghprb(project, this, getDescriptor().getPullRequests(project.getName()));
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "Stopping the ghprb trigger for project {0}", this.project);
        if (helper != null) {
            helper.stop();
            helper = null;
        }
        super.stop();
    }

    @Override
    public void run() {
        // triggers are always triggered on the cron, but we just no-op if we are using GitHub hooks.
        if (getUseGitHubHooks()) {
            return;
        }

        if (helper == null) {
        	logger.log(Level.INFO, "helper is null!");
        } else {
        	logger.log(Level.INFO, "helper is " + ReflectionToStringBuilder.toString(helper, ToStringStyle.MULTI_LINE_STYLE));
        	logger.log(Level.INFO, "trigger is " + ReflectionToStringBuilder.toString(helper.getTrigger(), ToStringStyle.MULTI_LINE_STYLE));
        }
        helper.run();
        getDescriptor().save();
    }

    public QueueTaskFuture<?> startJob(GhprbCause cause, GhprbRepository repo) {
        ArrayList<ParameterValue> values = getDefaultParameters();
        final String commitSha = cause.isMerged() ? "origin/pr/" + cause.getPullID() + "/merge" : cause.getCommit();
        values.add(new StringParameterValue("sha1", commitSha));
        values.add(new StringParameterValue("ghprbActualCommit", cause.getCommit()));
        
        setTriggerSender(cause, values);
        setCommitAuthor(cause, values);

        final StringParameterValue pullIdPv = new StringParameterValue("ghprbPullId", String.valueOf(cause.getPullID()));
        values.add(pullIdPv);
        values.add(new StringParameterValue("ghprbTargetBranch", String.valueOf(cause.getTargetBranch())));
        values.add(new StringParameterValue("ghprbSourceBranch", String.valueOf(cause.getSourceBranch())));
        values.add(new StringParameterValue("GIT_BRANCH", String.valueOf(cause.getSourceBranch())));
        // it's possible the GHUser doesn't have an associated email address
        values.add(new StringParameterValue("ghprbPullAuthorEmail", getString(cause.getAuthorEmail(), "")));
        values.add(new StringParameterValue("ghprbPullDescription", escapeQuotes(String.valueOf(cause.getShortDescription()))));
        values.add(new StringParameterValue("ghprbPullTitle", String.valueOf(cause.getTitle())));
        values.add(new StringParameterValue("ghprbPullLink", String.valueOf(cause.getUrl())));

        // add the previous pr BuildData as an action so that the correct change log is generated by the GitSCM plugin
        // note that this will be removed from the Actions list after the job is completed so that the old (and incorrect)
        // one isn't there
        return this.job.scheduleBuild2(job.getQuietPeriod(), cause, new ParametersAction(values), findPreviousBuildForPullId(pullIdPv), new RevisionParameterAction(cause.getCommit()));
    }
    
    private void setTriggerSender(GhprbCause cause, ArrayList<ParameterValue> values) {
        String triggerAuthor = "";
        String triggerAuthorEmail = "";

        try {
            triggerAuthor = getString(cause.getTriggerSender().getName(), "");
        } catch (Exception e) {}
        try {
            triggerAuthorEmail = getString(cause.getTriggerSender().getEmail(), "");
        } catch (Exception e) {}

        values.add(new StringParameterValue("ghprbTriggerAuthor", triggerAuthor));
        values.add(new StringParameterValue("ghprbTriggerAuthorEmail", triggerAuthorEmail));
        
    }

    private String escapeQuotes(String value) {
        return Pattern.compile("\"").matcher(value).replaceAll("\\\\\"");
    }

    private void setCommitAuthor(GhprbCause cause, ArrayList<ParameterValue> values) {
        String authorName = "";
        String authorEmail = "";
        if (cause.getCommitAuthor() != null) {
            authorName = getString(cause.getCommitAuthor().getName(), "");
            authorEmail = getString(cause.getCommitAuthor().getEmail(), "");
        }

        values.add(new StringParameterValue("ghprbActualCommitAuthor", authorName));
        values.add(new StringParameterValue("ghprbActualCommitAuthorEmail", authorEmail));
    }

    private String getString(String actual, String d) {
        return actual == null ? d : actual;
    }

    /**
     * Find the previous BuildData for the given pull request number; this may return null
     */
    private BuildData findPreviousBuildForPullId(StringParameterValue pullIdPv) {
        // find the previous build for this particular pull request, it may not be the last build
        for (Run<?, ?> r : job.getBuilds()) {
            ParametersAction pa = r.getAction(ParametersAction.class);
            if (pa != null) {
                for (ParameterValue pv : pa.getParameters()) {
                    if (pv.equals(pullIdPv)) {
                        for (BuildData bd : r.getActions(BuildData.class)) {
                            return bd;
                        }
                    }
                }
            }
        }
        return null;
    }

    private ArrayList<ParameterValue> getDefaultParameters() {
        ArrayList<ParameterValue> values = new ArrayList<ParameterValue>();
        ParametersDefinitionProperty pdp = this.job.getProperty(ParametersDefinitionProperty.class);
        if (pdp != null) {
            for (ParameterDefinition pd : pdp.getParameterDefinitions()) {
                if (pd.getName().equals("sha1"))
                    continue;
                values.add(pd.getDefaultParameterValue());
            }
        }
        return values;
    }

    public void addWhitelist(String author) {
        whitelist = whitelist + " " + author;
        try {
            this.job.save();
        } catch (IOException ex) {
            logger.log(Level.INFO, "Failed to save new whitelist", ex);
        }
    }

    public String getAdminlist() {
        if (adminlist == null) {
            return "";
        }
        return adminlist;
    }

    public Boolean getAllowMembersOfWhitelistedOrgsAsAdmin() {
        return allowMembersOfWhitelistedOrgsAsAdmin != null && allowMembersOfWhitelistedOrgsAsAdmin;
    }

    public String getWhitelist() {
        if (whitelist == null) {
            return "";
        }
        return whitelist;
    }

    public String getCommentFilePath() {
        return commentFilePath;
    }

    public String getOrgslist() {
        if (orgslist == null) {
            return "";
        }
        return orgslist;
    }

    public String getCron() {
        return cron;
    }

    public String getMsgSuccess() {
        return msgSuccess;
    }

    public String getMsgFailure() {
        return msgFailure;
    }

    public String getTriggerPhrase() {
        if (triggerPhrase == null) {
            return "";
        }
        return triggerPhrase;
    }

    public Boolean getOnlyTriggerPhrase() {
        return onlyTriggerPhrase != null && onlyTriggerPhrase;
    }

    public Boolean getUseGitHubHooks() {
        return useGitHubHooks != null && useGitHubHooks;
    }

    public Boolean getPermitAll() {
        return permitAll != null && permitAll;
    }

    public Boolean isAutoCloseFailedPullRequests() {
        if (autoCloseFailedPullRequests == null) {
            Boolean autoClose = getDescriptor().getAutoCloseFailedPullRequests();
            return (autoClose != null && autoClose);
        }
        return autoCloseFailedPullRequests;
    }

    public Boolean isDisplayBuildErrorsOnDownstreamBuilds() {
        if (displayBuildErrorsOnDownstreamBuilds == null) {
            Boolean displayErrors = getDescriptor().getDisplayBuildErrorsOnDownstreamBuilds();
            return (displayErrors != null && displayErrors);
        }
        return displayBuildErrorsOnDownstreamBuilds;
    }

    public List<GhprbBranch> getWhiteListTargetBranches() {
        if (whiteListTargetBranches == null) {
            return new ArrayList<GhprbBranch>();
        }
        return whiteListTargetBranches;
    }

    public String getCommitStatusContext() {
        return commitStatusContext;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @VisibleForTesting
    void setHelper(Ghprb helper) {
        this.helper = helper;
    }

    public GhprbBuilds getBuilds() {
        if (helper == null) {
            logger.log(Level.INFO, "The ghprb trigger for {0} wasn''t properly started - helper is null", this.project);
            return null;
        }
        return helper.getBuilds();
    }

    public GhprbRepository getRepository() {
        if (helper == null) {
            logger.log(Level.INFO, "The ghprb trigger for {0} wasn''t properly started - helper is null", this.project);
            return null;
        }
        return helper.getRepository();
    }

    public static final class DescriptorImpl extends TriggerDescriptor {
        // GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash
        private static final Pattern adminlistPattern = Pattern.compile("((\\p{Alnum}[\\p{Alnum}-]*)|\\s)*");

        /**
         * These settings only really affect testing. When Jenkins calls configure() then the formdata will be used to replace all of these fields. Leaving them here is useful for
         * testing, but must not be confused with a default. They also should not be used as the default value in the global.jelly file as this value is dynamic and will not be
         * retained once configure() is called.
         */
        private String whitelistPhrase = ".*add\\W+to\\W+whitelist.*";
        private String okToTestPhrase = ".*ok\\W+to\\W+test.*";
        private String retestPhrase = ".*test\\W+this\\W+please.*";
        private String skipBuildPhrase = ".*\\[skip\\W+ci\\].*";
        private String cron = "H/5 * * * *";
        private Boolean useComments = false;
        private Boolean useDetailedComments = false;
        private int logExcerptLines = 0;
        private String unstableAs = GHCommitState.FAILURE.name();
        private String msgSuccess = "Test PASSed.";
        private String msgFailure = "Test FAILed.";
        private List<GhprbBranch> whiteListTargetBranches;
        private String commitStatusContext = "";
        private Boolean autoCloseFailedPullRequests = false;
        private Boolean displayBuildErrorsOnDownstreamBuilds = false;
        private Set<GhprbGithubCredentials> credentials;

        /**
         * Deprecated fields
         */
        private String username;
        private String password;
        private String accessToken;
        private String serverAPIUrl;
        private String publishedURL;

        private String adminlist;
        private String requestForTestingPhrase;

        // map of jobs (by their fullName) abd their map of pull requests
        private Map<String, ConcurrentMap<Integer, GhprbPullRequest>> jobs;

        public DescriptorImpl() {
            load();
            if (jobs == null) {
                jobs = new HashMap<String, ConcurrentMap<Integer, GhprbPullRequest>>();
            }
            if (credentials == null) {
                credentials = new HashSet<GhprbGithubCredentials>(1);
            }

            if (credentials.size() <= 0 && serverAPIUrl != null && !serverAPIUrl.isEmpty()) {
                credentials.add(new GhprbGithubCredentials("default", serverAPIUrl, username, password, accessToken, publishedURL, false));
                save();
            }
        }
        
        public GhprbGithubCredentials getCredentials(String credentialsString) {
            for (GhprbGithubCredentials creds : credentials) {
                if (creds.getDisplayName().equals(credentialsString)) {
                    return creds;
                }
            }
            return null;
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "GitHub Pull Request Builder";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            adminlist = formData.getString("adminlist");
            requestForTestingPhrase = formData.getString("requestForTestingPhrase");
            whitelistPhrase = formData.getString("whitelistPhrase");
            okToTestPhrase = formData.getString("okToTestPhrase");
            retestPhrase = formData.getString("retestPhrase");
            skipBuildPhrase = formData.getString("skipBuildPhrase");
            cron = formData.getString("cron");
            useComments = formData.getBoolean("useComments");
            useDetailedComments = formData.getBoolean("useDetailedComments");
            logExcerptLines = formData.getInt("logExcerptLines");
            unstableAs = formData.getString("unstableAs");
            autoCloseFailedPullRequests = formData.getBoolean("autoCloseFailedPullRequests");
            displayBuildErrorsOnDownstreamBuilds = formData.getBoolean("displayBuildErrorsOnDownstreamBuilds");
            msgSuccess = formData.getString("msgSuccess");
            msgFailure = formData.getString("msgFailure");
            commitStatusContext = formData.getString("commitStatusContext");
            credentials = new HashSet<GhprbGithubCredentials>(1);
            
            logger.log(Level.INFO, "formData is " + ReflectionToStringBuilder.toString(formData, ToStringStyle.MULTI_LINE_STYLE));

            if (formData.has("credential")) {
            	logger.log(Level.INFO, "formData has credential");
            	
            	final Object credential = formData.get("credential");
                if (credential instanceof JSONArray) {
                	logger.log(Level.INFO, "credential is JSONArray \n" + ReflectionToStringBuilder.reflectionToString(credential, ToStringStyle.MULTI_LINE_STYLE));
                	
                    JSONArray credsArray = formData.getJSONArray("credential");
                    int length = credsArray.size();
                    for (int i = 0; i < length; ++i) {
                        credentials.add(new GhprbGithubCredentials(credsArray.getJSONObject(i)));
                    }
                } else if (formData.get("credential") instanceof JSONObject) {
                	logger.log(Level.INFO, "credential is JSONObject \n" + ReflectionToStringBuilder.reflectionToString(credential, ToStringStyle.MULTI_LINE_STYLE));
                	
                    credentials.add(new GhprbGithubCredentials(formData.getJSONObject("credential")));
                } else {
                	logger.log(Level.INFO, "credential is not JSONArray and JSONObject \n" + ReflectionToStringBuilder.reflectionToString(credential, ToStringStyle.MULTI_LINE_STYLE));
                }
            } else if (formData.has("serverAPIUrl")) {
            	logger.log(Level.INFO, "formData has serverAPIUrl(" + formData.getDouble("serverAPIUrl"));
                credentials.add(new GhprbGithubCredentials( "default",
                        formData.getString("serverAPIUrl"), formData.getString("username"), 
                        formData.getString("password"), formData.getString("accessToken"),
                        formData.getString("publishedURL"), false)
                );
            }

            save();
            return super.configure(req, formData);
        }

        public Set<GhprbGithubCredentials> getCredentials() {
            return credentials;
        }

        public ListBoxModel doFillCredentialsItems(@QueryParameter String credentials) {
            ListBoxModel items = new ListBoxModel();
            for (GhprbGithubCredentials creds : this.credentials) {

                items.add(creds, creds.toString());
                if (creds.toString().equals(credentials)) {
                    items.get(items.size() - 1).selected = true;
                }
            }

            return items;
        }

        public FormValidation doCheckAdminlist(@QueryParameter String value) throws ServletException {
            if (!adminlistPattern.matcher(value).matches()) {
                return FormValidation.error("GitHub username may only contain alphanumeric characters or dashes and cannot begin with a dash. Separate them with whitespaces.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCredentialsServerAPIUrl(@QueryParameter String value) {
            if ("https://api.github.com".equals(value)) {
                return FormValidation.ok();
            }
            if (value.endsWith("/api/v3") || value.endsWith("/api/v3/")) {
                return FormValidation.ok();
            }
            return FormValidation.warning("GitHub API URI is \"https://api.github.com\". GitHub Enterprise API URL ends with \"/api/v3\"");
        }

        public String getAdminlist() {
            return adminlist;
        }

        public String getRequestForTestingPhrase() {
            return requestForTestingPhrase;
        }

        public String getWhitelistPhrase() {
            return whitelistPhrase;
        }

        public String getOkToTestPhrase() {
            return okToTestPhrase;
        }

        public String getRetestPhrase() {
            return retestPhrase;
        }

        public String getSkipBuildPhrase() {
            return skipBuildPhrase;
        }

        public String getCron() {
            return cron;
        }

        public Boolean getUseComments() {
            return useComments;
        }

        public Boolean getUseDetailedComments() {
            return useDetailedComments;
        }

        public int getlogExcerptLines() {
            return logExcerptLines;
        }

        public Boolean getAutoCloseFailedPullRequests() {
            return autoCloseFailedPullRequests;
        }

        public Boolean getDisplayBuildErrorsOnDownstreamBuilds() {
            return displayBuildErrorsOnDownstreamBuilds;
        }

        public String getUnstableAs() {
            return unstableAs;
        }

        public String getMsgSuccess(AbstractBuild<?, ?> build) {
            String msg = msgSuccess;
            msg = Ghprb.replaceMacros(build, msg);
            return msg;
        }

        public String getMsgFailure(AbstractBuild<?, ?> build) {
            String msg = msgFailure;
            msg = Ghprb.replaceMacros(build, msg);
            return msg;
        }

        public boolean isUseComments() {
            return (useComments != null && useComments);
        }

        public boolean isUseDetailedComments() {
            return (useDetailedComments != null && useDetailedComments);
        }

        public String getCommitStatusContext() {
            return commitStatusContext;
        }

        public ConcurrentMap<Integer, GhprbPullRequest> getPullRequests(String projectName) {
            ConcurrentMap<Integer, GhprbPullRequest> ret;
            if (jobs.containsKey(projectName)) {
                Map<Integer, GhprbPullRequest> map = jobs.get(projectName);
                if (!(map instanceof ConcurrentMap)) {
                    map = new ConcurrentHashMap<Integer, GhprbPullRequest>(map);
                }
                jobs.put(projectName, (ConcurrentMap<Integer, GhprbPullRequest>) map);
                ret = (ConcurrentMap<Integer, GhprbPullRequest>) map;
            } else {
                ret = new ConcurrentHashMap<Integer, GhprbPullRequest>();
                jobs.put(projectName, ret);
            }
            return ret;
        }

        public FormValidation doTestGithubAccess(@QueryParameter("credential.serverApiUrl") final String serverApiUrl, @QueryParameter("credential.accessToken") final String accessToken,
                @QueryParameter("credential.username") final String username, @QueryParameter("credential.password") final String password) {
            try {
                GitHub gh;
                if (accessToken != null && !accessToken.isEmpty()) {
                    gh = GitHub.connectUsingOAuth(serverApiUrl, accessToken);
                } else {
                    gh = GitHub.connectToEnterprise(serverApiUrl, username, password);
                }
                GHMyself me = gh.getMyself();
                return FormValidation.ok("Connected to " + serverApiUrl + " as " + me.getName());
            } catch (IOException ex) {
                return FormValidation.error("GitHub API token couldn't be created: " + ex.getMessage());
            }
        }

        public FormValidation doCreateApiToken(@QueryParameter("credential.serverApiUrl") final String serverApiUrl, @QueryParameter("credential.username") final String username,
                @QueryParameter("credential.password") final String password) {
            try {
                GitHub gh = GitHub.connectToEnterprise(serverApiUrl, username, password);
                GHAuthorization token = gh.createToken(Arrays.asList(GHAuthorization.REPO_STATUS, GHAuthorization.REPO), "Jenkins GitHub Pull Request Builder", null);
                return FormValidation.ok("Access token created: " + token.getToken());
            } catch (IOException ex) {
                return FormValidation.error("GitHub API token couldn't be created: " + ex.getMessage());
            }
        }

        public List<GhprbBranch> getWhiteListTargetBranches() {
            return whiteListTargetBranches;
        }

        public void saveSetup() {
            save();
        }

        public GhprbGithubCredentials getDefaultCredentials() {
        	if (credentials != null && credentials.iterator().hasNext()) {
                return credentials.iterator().next();
            }
            return null;
        }
    }
}