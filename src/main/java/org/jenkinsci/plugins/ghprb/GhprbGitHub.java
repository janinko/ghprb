package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

/**
 * @author janinko
 */
public class GhprbGitHub {
	private static final Logger logger = Logger.getLogger(GhprbGitHub.class.getName());
	private GitHub gh;
	private String serverAPIUrl;
	private String accessToken;

        public GhprbGitHub(){
		this.serverAPIUrl = GhprbTrigger.getDscp().getServerAPIUrl();
		this.accessToken = GhprbTrigger.getDscp().getAccessToken();
        }

        public GhprbGitHub(String serverAPIUrl, String accessToken){
                this.serverAPIUrl = serverAPIUrl == "" ? GhprbTrigger.getDscp().getServerAPIUrl() : serverAPIUrl;
                this.accessToken = accessToken == "" ? GhprbTrigger.getDscp().getAccessToken() : accessToken;
        }

        private void connect() throws IOException{
		try {
			gh = GitHub.connectUsingOAuth(this.serverAPIUrl, this.accessToken);
		} catch(IOException e) {
			logger.log(Level.SEVERE, "Can''t connect to {0} using oauth", serverAPIUrl);
			throw e;
		}
        }

	public GitHub get() throws IOException{
		if(gh == null){
			connect();
		}
		return gh;
	}

	public boolean isUserMemberOfOrganization(String organisation, GHUser member){
		boolean orgHasMember = false;
		try {
			GHOrganization org = get().getOrganization(organisation);
			orgHasMember = org.hasMember(member);
			logger.log(Level.FINE, "org.hasMember(member)? user:{0} org: {1} == {2}",
					new Object[]{member.getLogin(), organisation, orgHasMember ? "yes" : "no"});

		} catch (IOException ex) {
			logger.log(Level.SEVERE, null, ex);
			return false;
		}
		return orgHasMember;
	}
}
