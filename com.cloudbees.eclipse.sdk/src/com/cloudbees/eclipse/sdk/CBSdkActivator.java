package com.cloudbees.eclipse.sdk;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

public class CBSdkActivator extends Plugin {
  // The plug-in ID
  public static final String PLUGIN_ID = "com.cloudbees.eclipse.sdk"; //$NON-NLS-1$

  // The shared instance
  private static CBSdkActivator plugin;

  /**
   * The constructor
   */
  public CBSdkActivator() {
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
   */
  @Override
  public void start(BundleContext context) throws Exception {
    super.start(context);
    plugin = this;
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
   */
  @Override
  public void stop(BundleContext context) throws Exception {
    plugin = null;
    super.stop(context);
  }

  /**
   * Returns the shared instance
   * 
   * @return the shared instance
   */
  public static CBSdkActivator getDefault() {
    return plugin;
  }
}