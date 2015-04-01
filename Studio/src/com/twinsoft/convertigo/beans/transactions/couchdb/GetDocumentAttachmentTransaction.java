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
 * $URL: $
 * $Author: $
 * $Revision: $
 * $Date: $
 */
package com.twinsoft.convertigo.beans.transactions.couchdb;

import java.io.File;
import java.util.List;

import javax.xml.namespace.QName;

import com.twinsoft.convertigo.engine.Engine;

public class GetDocumentAttachmentTransaction extends AbstractDocumentTransaction {

	private static final long serialVersionUID = -1731027540919633324L;

	private String u_attname = "";
	private String q_rev = "";
	
	public GetDocumentAttachmentTransaction() {
		super();
	}

	@Override
	public GetDocumentAttachmentTransaction clone() throws CloneNotSupportedException {
		GetDocumentAttachmentTransaction clonedObject =  (GetDocumentAttachmentTransaction) super.clone();
		return clonedObject;
	}
	
	@Override
	public List<CouchDbParameter> getDeclaredParameters() {
		return getDeclaredParameters(var_database, var_docid, var_docrev, var_filename, var_filepath);
	}
		
	@Override
	protected Object invoke() throws Exception {
		String docId = getParameterStringValue(var_docid);
		String docRev = getParameterStringValue(var_docrev);
		String attName = getParameterStringValue(var_filename);
		String attPath = getParameterStringValue(var_filepath);
		
		attPath = Engine.theApp.filePropertyManager.getFilepathFromProperty(attPath, getProject().getName());
		
		return getCouchClient().getDocumentAttachment(getTargetDatabase(), docId, docRev, attName, new File(attPath));
	}

	@Override
	public QName getComplexTypeAffectation() {
		return new QName(COUCHDB_XSD_NAMESPACE, "getDocumentAttachmentType");
	}

	public String getU_attname() {
		return u_attname;
	}

	public void setU_attname(String u_attname) {
		this.u_attname = u_attname;
	}

	public String getQ_rev() {
		return q_rev;
	}

	public void setQ_rev(String q_rev) {
		this.q_rev = q_rev;
	}
}

