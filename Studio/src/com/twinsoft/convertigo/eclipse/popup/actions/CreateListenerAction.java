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
 * $URL: svn://devus.twinsoft.fr/convertigo/CEMS_opensource/trunk/Studio/src/com/twinsoft/convertigo/eclipse/popup/actions/CreateDocumentAction.java $
 * $Author: nathalieh $
 * $Revision: 39108 $
 * $Date: 2015-02-04 15:23:39 +0100 (mer., 04 févr. 2015) $
 */

package com.twinsoft.convertigo.eclipse.popup.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import com.twinsoft.convertigo.beans.connectors.FullSyncConnector;
import com.twinsoft.convertigo.beans.core.Connector;
import com.twinsoft.convertigo.beans.core.DatabaseObject;
import com.twinsoft.convertigo.eclipse.views.projectexplorer.model.DatabaseObjectTreeObject;
import com.twinsoft.convertigo.eclipse.views.projectexplorer.model.ObjectsFolderTreeObject;
import com.twinsoft.convertigo.eclipse.views.projectexplorer.model.TreeObject;

public class CreateListenerAction extends DatabaseObjectCreateAction {

	public CreateListenerAction() {
		super("com.twinsoft.convertigo.beans.core.Listener");
	}
	
	public void selectionChanged(IAction action, ISelection selection) {
		try {
			boolean enable = true;
			super.selectionChanged(action, selection);
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			TreeObject treeObject = (TreeObject) structuredSelection.getFirstElement();
			if (treeObject instanceof ObjectsFolderTreeObject)
				enable = ((ObjectsFolderTreeObject)treeObject).folderType == ObjectsFolderTreeObject.FOLDER_TYPE_LISTENERS;
			else if (treeObject instanceof DatabaseObjectTreeObject) {
				DatabaseObject dbo = (DatabaseObject)treeObject.getObject();
				if (dbo instanceof Connector)
					enable = dbo instanceof FullSyncConnector;
			}
			action.setEnabled(enable);
		}
		catch (Exception e) {}
	}
	
}
