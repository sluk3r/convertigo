/*
 * Copyright (c) 2001-2011 Convertigo SA.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 *
 * $URL: http://sourceus/svn/convertigo/CEMS_opensource/trunk/Studio/src/com/twinsoft/convertigo/eclipse/views/projectexplorer/MobileDeviceTreeObject.java $
 * $Author: fabienb $
 * $Revision: 28379 $
 * $Date: 2011-09-27 11:38:59 +0200 (mar., 27 sept. 2011) $
 */

package com.twinsoft.convertigo.eclipse.views.projectexplorer.model;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.Viewer;

import com.twinsoft.convertigo.beans.core.MobileApplication;
import com.twinsoft.convertigo.eclipse.ConvertigoPlugin;
import com.twinsoft.convertigo.eclipse.views.projectexplorer.TreeParent;

public class MobileApplicationTreeObject extends DatabaseObjectTreeObject {

	public MobileApplicationTreeObject(Viewer viewer, MobileApplication object) {
		super(viewer, object);
	}

	public MobileApplicationTreeObject(Viewer viewer, MobileApplication object, boolean inherited) {
		super(viewer, object, inherited);
	}

	@Override
	public MobileApplication getObject() {
		return (MobileApplication) super.getObject();
	}

	@Override
	public void setParent(TreeParent parent) {
		super.setParent(parent);
		refreshResourceFolder();
	}
	
	private void refreshResourceFolder() {
		try {
			ProjectTreeObject projectTreeObject = getProjectTreeObject();
			if (projectTreeObject != null) {
				projectTreeObject.getIProject().getFolder(getObject().getRelativeResourcePath()).refreshLocal(IResource.DEPTH_INFINITE, null);
			}
		} catch (Exception e) {
			ConvertigoPlugin.logWarning(e, "Failed to refresh the mobile platform folder in resource view", false);
		}
	}
}
