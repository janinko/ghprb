package org.jenkinsci.plugins.ghprb;

import java.net.MalformedURLException;
import java.net.URL;

import com.google.common.annotations.VisibleForTesting;
import hudson.model.ModelObject;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;

public class GhprbGithubCredentials implements ModelObject{

	private final String name;
	private final URL serverApiUrl;
	private final String serverApiUrlString;
	private final String username;
	private final String password;
	private final String accessToken;
	private final boolean useToken;
	private final boolean ignoreBotUser;
	private final String publishedUrl;
	private GhprbGitHub gh;

	public String getName() {
		return name;
	}

	public URL getServerApiUrl() {
		return serverApiUrl;
	}

	public String getUsername() {
		return username;
	}
	public String getPassword() {
		return password;
	}
	public String getAccessToken() {
		return accessToken;
	}
	public boolean useToken() {
		return useToken;
	}
	public String getPublishedUrl() {
		return publishedUrl;
	}

	public boolean isIgnoreBotUser() {
		return ignoreBotUser;
	}


	public GhprbGithubCredentials() {
		name = null;
		serverApiUrlString = null;
		serverApiUrl = null;
		username = null;
		password = null;
		accessToken = null;
		useToken = false;
		publishedUrl = null;
		gh = null;
		ignoreBotUser = false;
	}

	public GhprbGithubCredentials(JSONObject creds) {
		name = creds.getString("name");
		serverApiUrlString = creds.getString("serverApiUrl");
		try {
			this.serverApiUrl = new URL(serverApiUrlString);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
		username = creds.getString("username");
		password = creds.getString("password");
		accessToken = creds.getString("accessToken");
		ignoreBotUser = creds.getBoolean("ignoreBotUser");
		useToken = accessToken != null && !accessToken.isEmpty();
		publishedUrl = creds.getString("publishedUrl");
		gh = new GhprbGitHub(this);
	}

	@DataBoundConstructor
	public GhprbGithubCredentials(String name, String serverApiUrl, String username,
			String password, String accessToken, String publishedUrl,
			boolean ignoreBotUser) {
		this.name = name;
		this.serverApiUrlString = serverApiUrl;
		try {
			this.serverApiUrl = new URL(serverApiUrl);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
		this.publishedUrl = publishedUrl;
		this.username = username;
		this.password = password;
		this.accessToken = accessToken;
		this.ignoreBotUser = ignoreBotUser;
		this.useToken = accessToken != null && !accessToken.isEmpty();
		gh = new GhprbGitHub(this);
	}

	public String getDisplayName() {
		return name + " " + serverApiUrlString;
	}

	public JSONObject toJSONObject() {
		JSONObject creds = new JSONObject();
		creds.put("name", name);
		creds.put("serverApiUrl", serverApiUrlString);
		creds.put("username", username);
		creds.put("password", password);
		creds.put("accessToken", accessToken);
		creds.put("publishedUrl", publishedUrl);
		creds.put("ignoreBotUser", ignoreBotUser);
		return creds;
	}


	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GhprbGithubCredentials)) {
			return false;
		}
		GhprbGithubCredentials creds = (GhprbGithubCredentials) o;
		boolean isSame = this.serverApiUrlString.equals(creds.serverApiUrlString);
		isSame &= this.name.equals(creds.name);
		return isSame;
	}

	@Override
	public String toString() {
		return name + " " + serverApiUrlString;
	}

	@Override
	public int hashCode() {
		return (toString()).hashCode();
	}

	public String getServerApiUrlString() {
		return serverApiUrlString;
	}

	public GhprbGitHub getGitHub() {
		if (gh == null) {
			gh = new GhprbGitHub(this);
		}
		return gh;
	}

	@VisibleForTesting
	void setGitHub(GhprbGitHub gh) {
		this.gh = gh;
	}

}
