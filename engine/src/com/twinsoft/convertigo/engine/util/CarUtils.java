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
 * $URL$
 * $Author$
 * $Revision$
 * $Date$
 */

package com.twinsoft.convertigo.engine.util;

import java.beans.IntrospectionException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.twinsoft.convertigo.beans.core.DatabaseObject;
import com.twinsoft.convertigo.beans.core.Project;
import com.twinsoft.convertigo.beans.core.TestCase;
import com.twinsoft.convertigo.engine.Engine;
import com.twinsoft.convertigo.engine.EngineException;
import com.twinsoft.convertigo.engine.helpers.WalkHelper;

public class CarUtils {

	public static void makeArchive(String projectName) throws EngineException {
		Project project = Engine.theApp.databaseObjectsManager.getProjectByName(projectName);
		makeArchive(project);
	}

	public static void makeArchive(Project project) throws EngineException {
		makeArchive(Engine.PROJECTS_PATH, project);
	}
	
	public static void makeArchive(Project project, List<TestCase> listTestCasesSelected) throws EngineException {
		makeArchive(Engine.PROJECTS_PATH, project, listTestCasesSelected);
	}

	public static void makeArchive(String dir, Project project) throws EngineException {
		makeArchive(dir, project, project.getName());
	}
	
	public static void makeArchive(String dir, Project project, List<TestCase> listTestCasesSelected) throws EngineException {
		makeArchive(dir, project, project.getName(), listTestCasesSelected);
	}
	
	public static void makeArchive(String dir, Project project, String exportName) throws EngineException {
		List<File> undeployedFiles=getUndeployedFiles(project.getName());	
		String projectName = project.getName();
		try {
			// Export the project
			String exportedProjectFileName = Engine.PROJECTS_PATH + "/" + projectName + "/" + projectName + ".xml";
			exportProject(project, exportedProjectFileName);
			
			// Create Convertigo archive
			String projectArchiveFilename = dir + "/" + exportName + ".car";
			ZipUtils.makeZip(projectArchiveFilename, Engine.PROJECTS_PATH + "/" + projectName, projectName, undeployedFiles);
		} catch(Exception e) {
			throw new EngineException("Unable to make the archive file for the project \"" + projectName + "\".", e);
		}
	}
	
	public static void makeArchive(String dir, Project project, String exportName, 
			List<TestCase> listTestCasesSelected) throws EngineException {
		List<File> undeployedFiles= getUndeployedFiles(project.getName());	
		String projectName = project.getName();
		try {
			// Export the project
			String exportedProjectFileName = Engine.PROJECTS_PATH + "/" + projectName + "/" + projectName + ".xml";
			exportProject(project, exportedProjectFileName, listTestCasesSelected);
			
			// Create Convertigo archive
			String projectArchiveFilename = dir + "/" + exportName + ".car";
			ZipUtils.makeZip(projectArchiveFilename, Engine.PROJECTS_PATH + "/" + projectName, projectName, undeployedFiles);
		} catch(Exception e) {
			throw new EngineException("Unable to make the archive file for the project \"" + projectName + "\".", e);
		}
	}

	private static List<File> getUndeployedFiles(String projectName){
		final List<File> undeployedFiles = new LinkedList<File>();
		
		File projectDir = new File(Engine.PROJECTS_PATH + "/" + projectName);
		
		File privateDir = new File(projectDir, "_private");
		undeployedFiles.add(privateDir);
		File dataDir = new File(projectDir, "_data");
		undeployedFiles.add(dataDir);
		File carFile = new File(projectDir, projectName + ".car");
		undeployedFiles.add(carFile);
		
		for (File file : projectDir.listFiles()) {
			if (file.getName().startsWith(".")) {
				undeployedFiles.add(file);
			}
		}
		
		new FileWalker(){

			@Override
			public void walk(File file) {
				String filename = file.getName(); 
				if (filename.equals(".svn") || filename.equals("CVS") || filename.equals("node_modules")) {
					undeployedFiles.add(file);
				} else {
					super.walk(file);					
				}
			}
			
		}.walk(projectDir);
		
		return undeployedFiles;
	}

	public static void exportProject(Project project, String fileName) throws EngineException {
		Document document = exportProject(project, new ArrayList<TestCase>());
		exportXMLProject(fileName, document);
	}
	
	public static void exportProject(Project project, String fileName, 
			List<TestCase> selectedTestCases) throws EngineException {
		Document document = exportProject(project, selectedTestCases);
		exportXMLProject(fileName, document);
	}
	
	private static void exportXMLProject(String fileName, Document document) throws EngineException {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			
			boolean isCR = FileUtils.isCRLF();
			
			Writer writer = isCR ? new StringWriter() : new FileWriterWithEncoding(fileName, "UTF-8");
			transformer.transform(new DOMSource(document), new StreamResult(writer));
			
			if (isCR) {
				String content = FileUtils.CrlfToLf(writer.toString());
				writer = new FileWriterWithEncoding(fileName, "UTF-8");
				writer.write(content);
			}
			
			writer.close();
		} catch (Exception e) {
			throw new EngineException("(CarUtils) exportProject failed", e);
		}
	}
	
	private static Document exportProject(Project project, final List<TestCase> selectedTestCases) 
			throws EngineException {
		try {
			final Document document = XMLUtils.getDefaultDocumentBuilder().newDocument();
			//            ProcessingInstruction pi = document.createProcessingInstruction("xml", "version=\"1.0\" encoding=\"UTF-8\"");
			//            document.appendChild(pi);
			final Element rootElement = document.createElement("convertigo");
			
			rootElement.setAttribute("version", com.twinsoft.convertigo.engine.Version.fullProductVersion);
			rootElement.setAttribute("engine", com.twinsoft.convertigo.engine.Version.version);
			rootElement.setAttribute("beans", com.twinsoft.convertigo.beans.Version.version);
			String studioVersion = "";
			try {
				Class<?> c = Class.forName("com.twinsoft.convertigo.eclipse.Version");
				studioVersion = (String)c.getDeclaredField("version").get(null);
			} catch (Exception e) {
			} catch (Throwable th) {
			}
			
			rootElement.setAttribute("studio", studioVersion);
			document.appendChild(rootElement);
			
			new WalkHelper() {
				protected Element parentElement = rootElement;
				
				@Override
				protected void walk(DatabaseObject databaseObject) throws Exception {
					Element parentElement = this.parentElement;
					
					Element element = parentElement;
					element = databaseObject.toXml(document);
					String name = " : " + databaseObject.getName();
					try {
						name = CachedIntrospector.getBeanInfo(databaseObject.getClass()).getBeanDescriptor().getDisplayName() + name;
					} catch (IntrospectionException e) {
						name = databaseObject.getClass().getSimpleName() + name;
					}
					Integer depth = (Integer) document.getUserData("depth");
					if (depth == null) {
						depth = 0;
					}
					
					String openpad = StringUtils.repeat("   ", depth);
					String closepad = StringUtils.repeat("   ", depth);
					parentElement.appendChild(document.createTextNode("\n"));
					parentElement.appendChild(document.createComment(StringUtils.rightPad(openpad + "<" + name + ">", 150)));
					
					if (databaseObject instanceof TestCase && selectedTestCases.size() > 0) { 
						if (selectedTestCases.contains((TestCase)databaseObject)) {
							parentElement.appendChild(element);
						} 
					} else {
						parentElement.appendChild(element);
					}
					
					document.setUserData("depth", depth + 1, null);
					
					this.parentElement = element;
					super.walk(databaseObject);
					
					element.appendChild(document.createTextNode("\n"));
					element.appendChild(document.createComment(StringUtils.rightPad(closepad + "</" + name + ">", 150)));
					document.setUserData("depth", depth, null);
					
					databaseObject.hasChanged = false;
					databaseObject.bNew = false;
					
					this.parentElement = parentElement;
				}				
				
			}.init(project);
			
			return document;
		} catch(Exception e) {
			throw new EngineException("Unable to export the project \"" + project.getName() + "\".", e);
		}
	}
	
	/*
	 * Returns an ArrayList of abstract pathnames denoting the files and directories
	 * in the directory denoted by this abstract pathname
	 * that satisfy the specified suffix
	 */
	public static ArrayList<File> deepListFiles(String sDir, String suffix) {
		final String _suffix = suffix;
		File[] all, files;
		File f, dir;
		
		dir = new File(sDir);
		
		all = dir.listFiles();
		
		files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				File file = new File(dir, name);
				return (file.getName().endsWith(_suffix));
			}
		});
		
		ArrayList<File> list = null, deep = null;
		if (files != null) {
			list = new ArrayList<File>(Arrays.asList(files));
		}
		
		if ((list != null) && (all != null)) {
			for (int i=0; i<all.length; i++) {
				f = all[i];
				if (f.isDirectory() && !list.contains(f)) {
					deep = deepListFiles(f.getAbsolutePath(), suffix);
					if (deep != null) {
						list.addAll(deep);
					}
				}
			}
		}
		
		return list;
	}
}