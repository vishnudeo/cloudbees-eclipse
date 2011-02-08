package com.cloudbees.eclipse.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import com.cloudbees.eclipse.core.domain.NectarInstance;
import com.cloudbees.eclipse.core.nectar.api.NectarBuildDetailsResponse;
import com.cloudbees.eclipse.core.nectar.api.NectarInstanceResponse;
import com.cloudbees.eclipse.core.nectar.api.NectarJobBuildsResponse;
import com.cloudbees.eclipse.core.nectar.api.NectarJobsResponse;
import com.cloudbees.eclipse.core.util.Utils;
import com.google.gson.Gson;

/**
 * Service to access Jenkins instances
 * 
 * @author ahti
 */
public class NectarService {

  /*private String url;
  private String label;*/
  private NectarInstance nectar;

  private static String lastJossoSessionId = null;

  public NectarService(NectarInstance nectar) {
    this.nectar = nectar;
  }

  /**
   * @param viewUrl
   *          full url for the view. <code>null</code> if all jobs from this instance.
   * @return
   * @throws CloudBeesException
   */
  public NectarJobsResponse getJobs(String viewUrl, IProgressMonitor monitor) throws CloudBeesException {
    if (viewUrl != null && !viewUrl.startsWith(nectar.url)) {
      throw new CloudBeesException("Unexpected view url provided! Service url: " + nectar.url + "; view url: "
          + viewUrl);
    }

    StringBuffer errMsg = new StringBuffer();

    try {
      monitor.beginTask("Fetching Job list for '" + nectar.label + "'...", IProgressMonitor.UNKNOWN);

      DefaultHttpClient httpclient = Utils.getAPIClient();

      Gson g = Utils.createGson();

      String reqUrl = viewUrl != null ? viewUrl : nectar.url;

      if (!reqUrl.endsWith("/")) {
        reqUrl = reqUrl + "/";
      }

      String uri = reqUrl + "api/json?tree=" + NectarJobsResponse.QTREE;
      HttpPost post = new HttpPost(uri);
      post.setHeader("Accept", "application/json");
      post.setHeader("Content-type", "application/json");

      String bodyResponse = retrieveWithLogin(httpclient, post, new SubProgressMonitor(monitor, 5), false);

      NectarJobsResponse views = null;
      try {
        views = g.fromJson(bodyResponse, NectarJobsResponse.class);
      } catch (Exception e) {
        //FIXME log properly
        //System.out.println("Illegal JSON response from the server for the request:\n" + uri + ":\n" + bodyResponse);
        throw e;
      }

      if (views != null) {
        views.serviceUrl = nectar.url;
      }

      return views;

    } catch (Exception e) {
      throw new CloudBeesException("Failed to get Jenkins jobs for '" + nectar.url + "'. "
          + (errMsg.length() > 0 ? " (" + errMsg + ")" : ""), e);
    } finally {
      monitor.done();
    }

  }

  public NectarInstanceResponse getInstance(IProgressMonitor monitor) throws CloudBeesException {
    StringBuffer errMsg = new StringBuffer();
  
    String reqUrl = nectar.url;
    if (!reqUrl.endsWith("/")) {
      reqUrl += "/";
    }
    reqUrl += "api/json?tree=" + NectarInstanceResponse.QTREE;

    try {
      DefaultHttpClient httpclient = Utils.getAPIClient();
      Gson g = Utils.createGson();

      HttpPost post = new HttpPost(reqUrl);
      post.setHeader("Accept", "application/json");
      post.setHeader("Content-type", "application/json");

      String bodyResponse = retrieveWithLogin(httpclient, post, monitor, false);

      if (bodyResponse == null) {
        throw new CloudBeesException("Failed to receive response from server");
      }

      NectarInstanceResponse instance = g.fromJson(bodyResponse, NectarInstanceResponse.class);

      if (instance != null) {
        instance.serviceUrl = nectar.url;
        instance.atCloud = nectar.atCloud;

        if (instance.views != null) {
          for (int i = 0; i < instance.views.length; i++) {
            instance.views[i].response = instance;
            instance.views[i].isPrimary = instance.primaryView.url.equals(instance.views[i].url);
          }
        }
      }

      return instance;

    } catch (OperationCanceledException e) {
      throw e;
    } catch (Exception e) {
      throw new CloudBeesException("Failed to get Jenkins views for '" + reqUrl + "'. "
          + (errMsg.length() > 0 ? " (" + errMsg + ")" : ""), e);
    }
  }

  private String retrieveWithLogin(DefaultHttpClient httpclient, HttpPost post, IProgressMonitor monitor, boolean expectRedirect)
      throws UnsupportedEncodingException,
      IOException, ClientProtocolException, CloudBeesException, Exception {
    String bodyResponse = null;

    //        if (lastJossoCookie != null) {
    //          reqUrl += "&JOSSO_SESSIONID=" + lastJossoCookie;
    //        }

    boolean tryToLogin = true; // false for BasicAuth, true for redirect login
    do {

      if ((nectar.atCloud || nectar.authenticate) && nectar.username != null && nectar.username.trim().length() > 0
          && nectar.password != null && nectar.password.trim().length() > 0) {
        //        post.addHeader("Authorization", "Basic " + Utils.toB64(nectar.username + ":" + nectar.password));
      }

      if (nectar.atCloud && lastJossoSessionId != null) { // basic auth failed
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("JOSSO_SESSIONID", lastJossoSessionId));
        post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8)); // 1

        post.addHeader("Cookie", "JOSSO_SESSIONID=" + lastJossoSessionId); // 2

        httpclient.getCookieStore().addCookie(new BasicClientCookie("JOSSO_SESSIONID", lastJossoSessionId)); // 3
      }

      HttpResponse resp = httpclient.execute(post);
      bodyResponse = Utils.getResponseBody(resp);

      if (nectar.atCloud && nectar.username != null && nectar.username.trim().length() > 0 && nectar.password != null
          && nectar.password.trim().length() > 0
          && (resp.getStatusLine().getStatusCode() == 302 || resp.getStatusLine().getStatusCode() == 301) && tryToLogin) { // redirect to login
        login(httpclient, post.getURI().toASCIIString(), resp.getFirstHeader("Location").getValue(),
            new SubProgressMonitor(monitor, 5));
        //httpclient.getCookieStore().clear();
        tryToLogin = false;
      } else {
        // check final outcome if we got what we asked for
        Utils.checkResponseCode(resp, expectRedirect);
        break;
      }

    } while (!tryToLogin);

    return bodyResponse;
  }

  private void login(DefaultHttpClient httpClient, final String referer, final String redirect,
      final IProgressMonitor monitor) throws Exception {
    try {
      String name = "Logging in to '" + nectar.label + "'...";
      monitor.beginTask(name, 5);
      monitor.setTaskName(name);

      //    httpClient.getCookieStore().clear();
      //    List<Cookie> oldCookies = new ArrayList<Cookie>(httpClient.getCookieStore().getCookies());

      String nextUrl = redirect;
      for (int i = 0; i < 20 && nextUrl != null; i++) {
        if (monitor.isCanceled()) {
          throw new OperationCanceledException();
        }
        HttpResponse lastResp = visitSite(httpClient, nextUrl, referer);

        Header redir = lastResp.getFirstHeader("Location");
        String redirUrl = redir == null ? null : redir.getValue();

        // page with login form
        if (redirUrl != null && nextUrl.lastIndexOf("josso_login") >= 0 && redirUrl.indexOf("login.do") >= 0) {
          redirUrl = redirUrl.substring(0, redirUrl.indexOf("login.do")) + "usernamePasswordLogin.do";
        }

        // shortcut to josso_login
        if (redirUrl != null && nextUrl.lastIndexOf("josso_security_check") >= 0 && nextUrl.indexOf("login.do") < 0) {
          redirUrl = nextUrl.substring(0, nextUrl.indexOf("josso_security_check")) + "josso_login/";
        }

        nextUrl = redirUrl;

        Header cookie = lastResp.getFirstHeader("Set-Cookie");
        if (cookie == null) {
          cookie = lastResp.getFirstHeader("SET-COOKIE");
        }
        if (cookie == null) {
          cookie = lastResp.getFirstHeader("Set-Cookie2");
        }
        if (cookie == null) {
          cookie = lastResp.getFirstHeader("SET-COOKIE2");
        }

        monitor.worked(1);

        if (cookie != null) {
          System.out.println("Cookie: " + cookie);
        }
        if (cookie != null && cookie.getValue().startsWith("JOSSO_SESSIONID=")) {
          break; // logged in ok
        }

      }

      for (Cookie cook : httpClient.getCookieStore().getCookies()) {
        if ("JOSSO_SESSIONID".equals(cook.getName())) {
          lastJossoSessionId = cook.getValue();
          //        break login; // ready
        }
      }

    } finally {
      monitor.done();
    }

    //System.out.println("JOSSO_SESSIONID=" + lastJossoSessionId);
  }

  private HttpResponse visitSite(DefaultHttpClient httpClient, String url, String refererUrl)
      throws IOException, ClientProtocolException, CloudBeesException {

    System.out.println("Visiting: " + url);

    HttpPost post = new HttpPost(url);
    post.addHeader("Referer", refererUrl);

    if (url.endsWith("usernamePasswordLogin.do")) {
      List<NameValuePair> nvps = new ArrayList<NameValuePair>();
      nvps.add(new BasicNameValuePair("josso_username", nectar.username));
      nvps.add(new BasicNameValuePair("josso_password", nectar.password));
      nvps.add(new BasicNameValuePair("josso_cmd", "login"));
      nvps.add(new BasicNameValuePair("josso_rememberme", "on"));
      post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
    }

    HttpResponse resp = httpClient.execute(post);
    String body = Utils.getResponseBody(resp);

    Header redir = resp.getFirstHeader("Location");
    System.out.println("\t" + resp.getStatusLine() + (redir != null ? (" -> " + redir.getValue()) : ""));

    return resp;
  }

  public String getLabel() {
    return nectar.label;
  }

  public String getUrl() {
    return nectar.url;
  }

  @Override
  public String toString() {
    if (nectar != null) {
      return "JenkinsService[jenkinsInstance=" + nectar + "]";
    }
    return super.toString();
  }

  public NectarBuildDetailsResponse getJobDetails(String jobUrl, final IProgressMonitor monitor)
      throws CloudBeesException {

    if (monitor != null) {
      monitor.setTaskName("Fetching Job details...");
    }

    if (jobUrl != null && !jobUrl.startsWith(nectar.url)) {
      throw new CloudBeesException("Unexpected view url provided! Service url: " + nectar.url + "; job url: " + jobUrl);
    }

    StringBuffer errMsg = new StringBuffer();

    String reqUrl = jobUrl;

    if (!reqUrl.endsWith("/")) {
      reqUrl = reqUrl + "/";
    }

    String reqStr = reqUrl + "api/json?tree=" + NectarBuildDetailsResponse.QTREE;

    try {
      DefaultHttpClient httpclient = Utils.getAPIClient();

      Gson g = Utils.createGson();


      HttpPost post = new HttpPost(reqStr);
      post.setHeader("Accept", "application/json");
      post.setHeader("Content-type", "application/json");

      String bodyResponse = retrieveWithLogin(httpclient, post, monitor, false);

      NectarBuildDetailsResponse details = g.fromJson(bodyResponse, NectarBuildDetailsResponse.class);

      if (details.fullDisplayName == null) {
        throw new CloudBeesException("Response does not contain required fields!");
      }

      return details;

    } catch (Exception e) {
      throw new CloudBeesException("Failed to get Jenkins jobs for '" + jobUrl + "'. "
          + (errMsg.length() > 0 ? " (" + errMsg + ")" : "") + "Request string:" + reqStr, e);
    }

  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((nectar == null) ? 0 : nectar.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NectarService other = (NectarService) obj;
    if (nectar == null) {
      if (other.nectar != null)
        return false;
    } else if (!nectar.equals(other.nectar))
      return false;
    return true;
  }

  public NectarJobBuildsResponse getJobBuilds(String jobUrl, final IProgressMonitor monitor) throws CloudBeesException {

    if (monitor != null) {
      monitor.setTaskName("Fetching Job builds...");
    }

    if (jobUrl != null && !jobUrl.startsWith(nectar.url)) {
      throw new CloudBeesException("Unexpected job url provided! Service url: " + nectar.url + "; job url: " + jobUrl);
    }

    StringBuffer errMsg = new StringBuffer();

    String reqUrl = jobUrl;

    if (!reqUrl.endsWith("/")) {
      reqUrl = reqUrl + "/";
    }

    String reqStr = reqUrl + "api/json?tree=" + NectarJobBuildsResponse.QTREE;

    try {
      DefaultHttpClient httpclient = Utils.getAPIClient();

      Gson g = Utils.createGson();

      HttpPost post = new HttpPost(reqStr);
      post.setHeader("Accept", "application/json");
      post.setHeader("Content-type", "application/json");

      String bodyResponse = retrieveWithLogin(httpclient, post, monitor, false);

      NectarJobBuildsResponse details = g.fromJson(bodyResponse, NectarJobBuildsResponse.class);

      if (details.name == null) {
        throw new CloudBeesException("Response does not contain required fields!");
      }

      details.serviceUrl = nectar.url;

      return details;

    } catch (Exception e) {
      throw new CloudBeesException("Failed to get Jenkins jobs for '" + jobUrl + "'. "
          + (errMsg.length() > 0 ? " (" + errMsg + ")" : "") + "Request string:" + reqStr, e);
    }

  }

  public void invokeBuild(String jobUrl, IProgressMonitor monitor) throws CloudBeesException {
    monitor.setTaskName("Invoking build request...");

    if (jobUrl != null && !jobUrl.startsWith(nectar.url)) {
      throw new CloudBeesException("Unexpected job url provided! Service url: " + nectar.url + "; job url: " + jobUrl);
    }

    StringBuffer errMsg = new StringBuffer();

    String reqUrl = jobUrl;

    if (!reqUrl.endsWith("/")) {
      reqUrl = reqUrl + "/";
    }

    String reqStr = reqUrl + "build";

    try {
      DefaultHttpClient httpclient = Utils.getAPIClient();

      HttpPost post = new HttpPost(reqStr);

      retrieveWithLogin(httpclient, post, monitor, true);
    } catch (Exception e) {
      throw new CloudBeesException("Failed to get invoke Build for '" + jobUrl + "'. "
          + (errMsg.length() > 0 ? " (" + errMsg + ")" : "") + "Request string:" + reqStr, e);
    }
  }

}
