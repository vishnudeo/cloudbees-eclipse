/*******************************************************************************
 * Copyright (c) 2013 Cloud Bees, Inc.
 * All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	Cloud Bees, Inc. - initial API and implementation 
 *******************************************************************************/
package com.cloudbees.eclipse.core;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class NatureUtil {

  public static void addNatures(final IProject project, final String[] natureIds,
      final IProgressMonitor progressMonitor) throws CoreException {
    //JavaCore.create(project);
    IProjectDescription description = project.getDescription();
    HashSet<String> oldNatureIds = new HashSet<String>(Arrays.asList(description.getNatureIds()));
    oldNatureIds.addAll(Arrays.asList(natureIds));
    description.setNatureIds(oldNatureIds.toArray(new String[oldNatureIds.size()]));
    project.setDescription(description, progressMonitor);
  }

  public static void removeNatures(final IProject project, final String[] natureIds,
      final IProgressMonitor progressMonitor) throws CoreException {
    IProjectDescription description = project.getDescription();
    HashSet<String> oldNatureIds = new HashSet<String>(Arrays.asList(description.getNatureIds()));
    oldNatureIds.removeAll(Arrays.asList(natureIds));
    description.setNatureIds(oldNatureIds.toArray(new String[oldNatureIds.size()]));
    project.setDescription(description, progressMonitor);
  }

  /**
   * @param resource
   * @return true if the resource is a project with the CloudBees nature enabled
   */
  public static boolean isEnabledFor(final IResource resource, final String natureId) {
    if (resource == null) {
      return false;
    }
    if (!resource.exists()) {
      return false;
    }
    IProject project = null;
    if (resource.getType() != IResource.PROJECT) {
      project = resource.getProject();
      if (project == null) {
        return false;
      }
      if (!project.exists()) {
        return false;
      }
    } else {
      project = (IProject) resource;
    }

    try {
      return project.isNatureEnabled(natureId);
    } catch (CoreException e) {
      CloudBeesCorePlugin.getDefault().getLogger().error(e);
      return false;
    }
  }

}
