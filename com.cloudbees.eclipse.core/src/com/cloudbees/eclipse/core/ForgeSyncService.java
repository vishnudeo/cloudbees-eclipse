package com.cloudbees.eclipse.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.cloudbees.eclipse.core.internal.forge.ForgeEGitSync;
import com.cloudbees.eclipse.core.internal.forge.ForgeSubclipseSync;
import com.cloudbees.eclipse.core.internal.forge.ForgeSubversiveSync;
import com.cloudbees.eclipse.core.internal.forge.ForgeSync;

/**
 * Main service for syncing Forge repositories and Eclipse repository entries. Currently supported providers are EGit,
 * Sublclipse, Subversive
 * 
 * @author ahtik
 */
public class ForgeSyncService {

  private List<ForgeSync> providers = new ArrayList<ForgeSync>();

  public ForgeSyncService() {
    if (bundleActive("org.tigris.subversion.subclipse.core")) {
      providers.add(new ForgeSubclipseSync());
    }
    if (bundleActive("org.eclipse.team.svn.core")) {
      providers.add(new ForgeSubversiveSync());
    }
    if (bundleActive("org.eclipse.egit.core") && bundleActive("org.eclipse.jgit")) {
      providers.add(new ForgeEGitSync());
    }

  }

  private boolean bundleActive(String bundleName) {
    Bundle bundle = Platform.getBundle(bundleName);
    if (bundle != null && (bundle.getState() == Bundle.ACTIVE || bundle.getState() == Bundle.STARTING)) {
      return true;
    }
    return false;
  }

  public void sync(ForgeSync.TYPE type, Properties props, IProgressMonitor monitor) throws CloudBeesException {
    for (ForgeSync provider : providers) {
      provider.sync(type, props, monitor);
    }
  }

}