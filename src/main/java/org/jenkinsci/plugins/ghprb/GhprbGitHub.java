package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import com.google.common.annotations.VisibleForTesting;

/**
 * @author janinko
 */
public class GhprbGitHub {
    private static final Logger logger = Logger.getLogger(GhprbGitHub.class.getName());
    private GitHub gh;
    private final GhprbGithubCredentials credentials;

    public GhprbGitHub(GhprbGithubCredentials credentials){
        this.credentials = credentials;
    }


    private void connect() throws IOException {
        String serverApiUrl = credentials.getServerApiUrlString();
        String accessToken = credentials.getAccessToken();
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                gh = new GitHubBuilder()
                        .withEndpoint(serverApiUrl)
                        .withOAuthToken(accessToken)
                        .withConnector(new HttpConnectorWithJenkinsProxy()).build();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Can''t connect to {0} using oauth", serverApiUrl);
                throw e;
            }
        } else {
            String username = credentials.getUsername();
            String password = credentials.getPassword();
            if (serverApiUrl.contains("api/v3")) {
                gh = GitHub.connectToEnterprise(serverApiUrl, username,password);
            } else {
                gh = new GitHubBuilder()
                        .withPassword(username, password)
                        .withConnector(new HttpConnectorWithJenkinsProxy())
                        .build();
            }
        }
    }

    public GitHub get() throws IOException {
        if (gh == null) {
            connect();
        }
        return gh;
    }
    
    @VisibleForTesting
    void setGitHub(GitHub gh) {
        this.gh = gh;
    }

    public boolean isUserMemberOfOrganization(String organisation, GHUser member) {
        boolean orgHasMember = false;
        try {
            GHOrganization org = get().getOrganization(organisation);
            orgHasMember = org.hasMember(member);
            logger.log(Level.FINE, "org.hasMember(member)? user:{0} org: {1} == {2}", 
                    new Object[] { member.getLogin(), organisation, orgHasMember ? "yes" : "no" });

        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
        return orgHasMember;
    }

    public String getBotUserLogin() {
        try {
            return get().getMyself().getLogin();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
