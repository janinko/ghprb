package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;

/**
 * @author janinko
 */
public class GhprbBuilds {
	private static final Logger logger = Logger.getLogger(GhprbBuilds.class.getName());
	private GhprbTrigger trigger;
	private GhprbRepository repo;

	public GhprbBuilds(GhprbTrigger trigger, GhprbRepository repo){
		this.trigger = trigger;
		this.repo = repo;
	}

	public void build(GhprbPullRequest pr) {
		String message = pr.isMergeable() ? "Merged build triggered" : " Build triggered";
		repo.createCommitStatus(pr.getHead(), GHCommitState.PENDING, null, message, pr.getId());

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(), pr.isMergeable(), pr.getTarget(), pr.getAuthorEmail(), pr.getTitle());
		QueueTaskFuture<?> build = trigger.startJob(cause);

		if (build == null) {
			logger.log(Level.SEVERE, "Job did not start");
		}
	}

	private GhprbCause getCause(AbstractBuild build){
		Cause cause = build.getCause(GhprbCause.class);
		if(cause == null || (!(cause instanceof GhprbCause))) return null;
		return (GhprbCause) cause;
	}

	public void onStarted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		if(c == null) return;

		String message = String.format("%s #%d started", c.isMerged() ? "Merged build" : "Build", build.getNumber());
		repo.createCommitStatus(build, GHCommitState.PENDING, message, c.getPullID());

		try {
			build.setDescription("<a title=\"" + c.getTitle() + "\" href=\"" + repo.getRepoUrl()+"/pull/"+c.getPullID()+"\">PR #"+c.getPullID()+"</a>: " + c.getAbbreviatedTitle());
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can't update build description", ex);
		}
	}

	public void onCompleted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		if(c == null) return;

		GHCommitState state;
		String verb;
		if (build.getResult() == Result.SUCCESS) {
			state = GHCommitState.SUCCESS;
			verb = "succeeded";
		}
		else if (build.getResult() == Result.UNSTABLE) {
			state = GHCommitState.valueOf(GhprbTrigger.getDscp().getUnstableAs());
			verb = "found unstable";
		}
		else {
			state = GHCommitState.FAILURE;
			verb = "failed";
		}

		String message =
				String.format("%s #%d %s in %s", c.isMerged() ? "Merged build" : "Build", build.getNumber(), verb,
						build.getDurationString());
		repo.createCommitStatus(build, state, message, c.getPullID());

		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		if (publishedURL != null && !publishedURL.isEmpty()) {
			String msg;
			if (state == GHCommitState.SUCCESS) {
				msg = GhprbTrigger.getDscp().getMsgSuccess();
			} else {
				msg = GhprbTrigger.getDscp().getMsgFailure();
			}
			repo.addComment(c.getPullID(), msg + "\nRefer to this link for build results: " + publishedURL + build.getUrl());
		}

		// close failed pull request automatically
		if (state == GHCommitState.FAILURE && trigger.isAutoCloseFailedPullRequests()) {

			try {
				GHPullRequest pr = repo.getPullRequest(c.getPullID());

				if (pr.getState().equals(GHIssueState.OPEN)) {
					repo.closePullRequest(c.getPullID());
				}
			} catch (IOException ex) {
				logger.log(Level.SEVERE, "Can't close pull request", ex);
			}
		}
	}
}
