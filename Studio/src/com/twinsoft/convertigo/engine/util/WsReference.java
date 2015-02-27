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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.wsdl.Definition;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.constants.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.wsdl.WsdlInterface;
import com.eviware.soapui.impl.wsdl.WsdlOperation;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlRequest;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlImporter;
import com.eviware.soapui.model.iface.MessagePart;
import com.eviware.soapui.model.settings.Settings;
import com.eviware.soapui.settings.ProxySettings;
import com.eviware.soapui.settings.WsdlSettings;
import com.twinsoft.convertigo.beans.common.XMLVector;
import com.twinsoft.convertigo.beans.common.XmlQName;
import com.twinsoft.convertigo.beans.connectors.HttpConnector;
import com.twinsoft.convertigo.beans.core.Project;
import com.twinsoft.convertigo.beans.references.WebServiceReference;
import com.twinsoft.convertigo.beans.transactions.AbstractHttpTransaction;
import com.twinsoft.convertigo.beans.transactions.XmlHttpTransaction;
import com.twinsoft.convertigo.beans.variables.RequestableHttpMultiValuedVariable;
import com.twinsoft.convertigo.beans.variables.RequestableHttpVariable;
import com.twinsoft.convertigo.engine.Engine;
import com.twinsoft.convertigo.engine.EngineException;
import com.twinsoft.convertigo.engine.EnginePropertiesManager.ProxyMode;
import com.twinsoft.convertigo.engine.PacManager.PacInfos;
import com.twinsoft.convertigo.engine.Version;

public class WsReference {
	private WebServiceReference reference = null;
	private boolean updateMode = false;
	
	public WsReference(String wsdlURL) {
	   	reference = new WebServiceReference();
	   	reference.setUrlpath(wsdlURL);
	}

	public WsReference(WebServiceReference reference) {
		this.reference = reference;
	}

	private WebServiceReference getReference() {
		return reference;
	}
	
	protected HttpConnector importIntoAuthenticated(Project project, String login, String password, boolean updateMode) throws Exception {
		HttpConnector httpConnector = null;
		//We add login/password into the connection
		System.setProperty("soapui.loader.username", login);
		System.setProperty("soapui.loader.password", password);
		
		try{
			tryAuthentication(login, password);
			reference.setNeedAuthentication(true);
		} catch (Exception e) {
			throw new Exception ("Authentication failure!", e);
		}
		httpConnector = importInto(project, updateMode);

		//We clear login/password
		System.setProperty("soapui.loader.username", "");
		System.setProperty("soapui.loader.password", "");
		
		return httpConnector;
	}
	
	private void tryAuthentication(String username, String password) throws Exception {
		URL urlToConnect = reference.getUrl();

        HttpClient client = new HttpClient();

		client.getState().setCredentials(
				new AuthScope(urlToConnect.getHost(), urlToConnect.getPort()),
				new UsernamePasswordCredentials(username, password)
		);
        
        GetMethod get = new GetMethod(reference.getUrlpath());
        get.setDoAuthentication( true );
        
        int statuscode = client.executeMethod(get);
        
        if (statuscode == HttpStatus.SC_UNAUTHORIZED) {
        	throw new Exception(HttpStatus.SC_UNAUTHORIZED + " - Unauthorized connection!");
        }
	}
	
	protected HttpConnector importInto(Project project, boolean updateMode) throws Exception {
		WebServiceReference webServiceReference = null;
		HttpConnector httpConnector = null;
		this.updateMode = updateMode;
		
		try{
			if (project != null) {
				webServiceReference = getReference();
				if (webServiceReference.getParent() == null)
	   		   		project.add(webServiceReference);
				
				httpConnector = importWebService(webServiceReference);
	   			if (!updateMode && httpConnector != null)
	   				project.add(httpConnector);
			}
		} catch (Exception e) {
			try {
				if (webServiceReference != null) {
					project.remove(webServiceReference);
					webServiceReference = null;
				}
				if (httpConnector != null) {
					project.remove(httpConnector);
					httpConnector = null;
				}
			}
			catch (Exception ex) {}
			throw new EngineException("Unable to import the web service reference : Invalid WSDL", e);
		}
		return httpConnector;
	}
	
	public void setTaskLabel(String text) {}
	
	static public int getTotalTaskNumber() {
		return 9;
	}
	
	private HttpConnector importWebService(WebServiceReference webServiceReference) throws Exception
	{
		String projectName = webServiceReference.getParent().getName();
		String wsdlUrl = webServiceReference.getUrlpath();
		setTaskLabel("Importing from \""+wsdlUrl+"\"...");

		// Configure SoapUI settings
		boolean soapuiSettingsChanged = false;
		Settings settings = SoapUI.getSettings();
		if (settings != null) {
			// WSDL
			if (!settings.getBoolean(WsdlSettings.XML_GENERATION_ALWAYS_INCLUDE_OPTIONAL_ELEMENTS)) {
				settings.setBoolean(WsdlSettings.XML_GENERATION_ALWAYS_INCLUDE_OPTIONAL_ELEMENTS, true);
				soapuiSettingsChanged = true;
			}
			if (!settings.getBoolean(WsdlSettings.XML_GENERATION_TYPE_COMMENT_TYPE)) {
				settings.setBoolean(WsdlSettings.XML_GENERATION_TYPE_COMMENT_TYPE, true);
				soapuiSettingsChanged = true;
			}
			if (!settings.getBoolean(WsdlSettings.XML_GENERATION_TYPE_EXAMPLE_VALUE)) {
				settings.setBoolean(WsdlSettings.XML_GENERATION_TYPE_EXAMPLE_VALUE, true);
				soapuiSettingsChanged = true;
			}
			if (settings.getBoolean(WsdlSettings.XML_GENERATION_SKIP_COMMENTS)) {
				settings.setBoolean(WsdlSettings.XML_GENERATION_SKIP_COMMENTS, false);
				soapuiSettingsChanged = true;
			}
			
			// PROXY
			ProxyMode proxyMode = Engine.theApp.proxyManager.proxyMode;
			String proxyExcludes = StringUtils.join(Engine.theApp.proxyManager.getBypassDomains(), ",");
			String proxyServer = Engine.theApp.proxyManager.getProxyServer();
			int proxyPort = Engine.theApp.proxyManager.getProxyPort();
			String proxyUser = Engine.theApp.proxyManager.getProxyUser();
			String proxyPwd = Engine.theApp.proxyManager.getProxyPassword();
			
			boolean enableProxy = Engine.theApp.proxyManager.isEnabled();
			if (enableProxy) {
				if (proxyMode == ProxyMode.auto) {
					try {
						URL url = new URL(wsdlUrl);
						PacInfos pacInfos = Engine.theApp.proxyManager.getPacInfos(url.toString(), url.getHost());
						if (pacInfos != null) {
							proxyServer = pacInfos.getServer();
							proxyPort = pacInfos.getPort();
						}
					}
					catch (Exception e) {}
				}
				
				if (!settings.getBoolean(ProxySettings.ENABLE_PROXY)) {
					settings.setBoolean(ProxySettings.ENABLE_PROXY, true);
					soapuiSettingsChanged = true;
				}
				if (!proxyExcludes.equals(settings.getString(ProxySettings.EXCLUDES, null))) {
					settings.setString(ProxySettings.EXCLUDES, proxyExcludes);
					soapuiSettingsChanged = true;
				}
				if (!proxyServer.equals(settings.getString(ProxySettings.HOST, null))) {
					settings.setString(ProxySettings.HOST, proxyServer);
					soapuiSettingsChanged = true;
				}
				if (!String.valueOf(proxyPort).equals(settings.getString(ProxySettings.PORT, null))) {
					settings.setString(ProxySettings.PORT, String.valueOf(proxyPort));
					soapuiSettingsChanged = true;
				}
				if (!proxyUser.equals(settings.getString(ProxySettings.USERNAME, null))) {
					settings.setString(ProxySettings.USERNAME, proxyUser);
					soapuiSettingsChanged = true;
				}
				if (!proxyPwd.equals(settings.getString(ProxySettings.PASSWORD, null))) {
					settings.setString(ProxySettings.PASSWORD, proxyPwd);
					soapuiSettingsChanged = true;
				}
			}
			else {
				if (settings.getBoolean(ProxySettings.ENABLE_PROXY)) {
					settings.setBoolean(ProxySettings.ENABLE_PROXY, false);
					soapuiSettingsChanged = true;
				}
			}
		}
		if (soapuiSettingsChanged)
			SoapUI.saveSettings();
		
		// Import WSDL using SoapUI
		HttpConnector httpConnector = null;
		String projectDir = Engine.PROJECTS_PATH + "/"+ projectName;
		
	   	WsdlProject wsdlProject = new WsdlProject();
	   	WsdlInterface[] wsdls = WsdlImporter.importWsdl(wsdlProject, wsdlUrl); 	
	   	
	   	boolean hasDefaultTransaction;
	   	WsdlInterface iface;
	   	
	   	for (int i=0; i<wsdls.length; i++) {
		   	iface = wsdls[i];
		   	if (iface != null) {
			   	
			   	// Export WSDL file(s) to project directory and modify reference local file path
		   		String folderName = iface.getBindingName().getLocalPart();
		   		File filePath = new File(projectDir + "/wsdl/" + folderName);;
		   		if (!updateMode) {
			   		for (int index = 1; filePath.exists(); index++) {
			   			filePath = new File(projectDir + "/wsdl/" + folderName + index);
			   		}
		   		} 
		   		
			   	String wsdlPath = iface.getWsdlContext().export(filePath.getPath());
			   	wsdlPath = new File(wsdlPath).toURI().getPath();
			   	webServiceReference.setFilepath(".//" + wsdlPath.substring(wsdlPath.indexOf("/wsdl") + 1));
			   		
			   	webServiceReference.setName("Import_WS_" + filePath.getName());
			   	
			   	Definition definition = iface.getWsdlContext().getDefinition();
			   	XmlSchemaCollection xmlSchemaCollection = WSDLUtils.readSchemas(definition);
			   	XmlSchema xmlSchema = xmlSchemaCollection.schemaForNamespace(definition.getTargetNamespace());
			   	
			   	if (!updateMode) {
			   		httpConnector = createConnector(iface);
			   		if (httpConnector != null) {
			   		   	hasDefaultTransaction = false;
					   	for (int j=0; j<iface.getOperationCount(); j++) {
					   		WsdlOperation wsdlOperation = (WsdlOperation)iface.getOperationAt(j);
					   		List<RequestableHttpVariable> variables = new ArrayList<RequestableHttpVariable>();
						   	XmlHttpTransaction xmlHttpTransaction = createTransaction(xmlSchemaCollection, xmlSchema, iface, wsdlOperation, variables, projectName, httpConnector);
				   			// Adds transaction
					   		if (xmlHttpTransaction != null) {
					   			httpConnector.add(xmlHttpTransaction);
					   			if (!hasDefaultTransaction) {
					   				xmlHttpTransaction.setByDefault();
					   				hasDefaultTransaction = true;
					   			}
					   		}
					   	}
			   		}
			   	} else {
			   		//
			   	}
		   	}
	   	}
	   	return httpConnector;
	}
	
	private HttpConnector createConnector(WsdlInterface iface) throws EngineException {
   		HttpConnector httpConnector = null;
   		if (iface != null) {
   			httpConnector = new HttpConnector();
   	   		httpConnector.bNew = true;

   	   		String comment = iface.getDescription();
   	   		try {comment = (comment.equals("")? iface.getBinding().getDocumentationElement().getTextContent():comment);} catch (Exception e) {}
   	   		httpConnector.setComment(comment);
   	   		
   	   		String connectorName = StringUtils.normalize(iface.getBindingName().getLocalPart());
   	   		httpConnector.setName(connectorName);
   		   	setTaskLabel("Creating http connector \""+connectorName+"\"...");
   		   	
   		   	String[] endPoints = iface.getEndpoints();
   		   	String endPoint = endPoints[0];
   		   	
   		   	String host, server, port, baseDir;
   		   	boolean isHttps = endPoint.startsWith("https://");
   		   	if (endPoint.indexOf("://") != -1) {
	   		   	int beginIndex = endPoint.indexOf("://") + "://".length();
	   		   	int endIndex = endPoint.indexOf("/", beginIndex);
	   		   	if (endIndex != -1) {
	   		   		host = endPoint.substring(beginIndex, endIndex);
	   		   		baseDir = endPoint.substring(endIndex+1);
	   		   	}
	   		   	else {
	   		   		host = endPoint.substring(beginIndex);
	   		   		baseDir = "";
	   		   	}
	   		   	
	   		   	int middleIndex = host.indexOf(":");
	   		   	if (middleIndex != -1) {
	   		   		server = host.substring(0, middleIndex);
	   		   		port = host.substring(middleIndex+1);
	   		   	}
	   		   	else {
	   		   		server = host;
	   		   		port = isHttps ? "443":"80";
	   		   	}
   		   	}
   		   	else {
   		   		server = endPoint;
   		   		port = isHttps ? "443":"80";
   		   		baseDir = "";
   		   	}
   		   	
   		   	httpConnector.setHttps(isHttps);
   		   	httpConnector.setServer(server);
   		   	httpConnector.setPort(Integer.valueOf(port).intValue());
		   	httpConnector.setBaseDir("/"+baseDir);
   		   	httpConnector.hasChanged = true;
   		}
	   	return httpConnector;
	}
	
	private XmlHttpTransaction createTransaction(XmlSchemaCollection xmlSchemaCollection, XmlSchema xmlSchema, WsdlInterface iface, WsdlOperation operation, List<RequestableHttpVariable> variables, String projectName, HttpConnector httpConnector) throws ParserConfigurationException, SAXException, IOException, EngineException {
		XmlHttpTransaction xmlHttpTransaction = null;
	   	WsdlRequest request;
	   	String requestXml;
	   	String transactionName, comment;
	   	String operationName;
	   	
	   	if (operation != null) {
	   		comment = operation.getDescription();
	   		try {comment = (comment.equals("") ? operation.getBindingOperation().getDocumentationElement().getTextContent():comment);} catch (Exception e) {}
	   		operationName = operation.getName();
		   	transactionName = StringUtils.normalize("C"+operationName);
	   		xmlHttpTransaction = new XmlHttpTransaction();
	   		xmlHttpTransaction.bNew = true;
	   		xmlHttpTransaction.setHttpVerb(AbstractHttpTransaction.HTTP_VERB_POST);
	   		xmlHttpTransaction.setName(transactionName);
	   		xmlHttpTransaction.setComment(comment);
	   		setTaskLabel("Creating transaction \""+transactionName+"\"...");
	   		
	   		// Set encoding (UTF-8 by default)
	   		xmlHttpTransaction.setEncodingCharSet("UTF-8");
	   		xmlHttpTransaction.setXmlEncoding("UTF-8");
	   		
	   		// Ignore SOAP elements in response
	   		xmlHttpTransaction.setIgnoreSoapEnveloppe(true);
	   		
   			// Adds parameters
	   		setTaskLabel("Setting http parameters...");
   			XMLVector<XMLVector<String>> parameters = new XMLVector<XMLVector<String>>();
   			XMLVector<String> xmlv;
   			xmlv = new XMLVector<String>();
   			xmlv.addElement("Content-Type");
   			xmlv.addElement("text/xml");
   			parameters.addElement(xmlv);
   			
   			xmlv = new XMLVector<String>();
   			xmlv.addElement("Host");
   			xmlv.addElement(httpConnector.getServer());
   			parameters.addElement(xmlv);

   			xmlv = new XMLVector<String>();
   			xmlv.addElement("SOAPAction");
   			xmlv.addElement(""); // fix #4215 - SOAPAction header must be empty
   			parameters.addElement(xmlv);

   			xmlv = new XMLVector<String>();
   			xmlv.addElement("user-agent");
   			xmlv.addElement("Convertigo EMS "+ Version.fullProductVersion);
   			parameters.addElement(xmlv);
   			
   			xmlHttpTransaction.setHttpParameters(parameters);
   			
   			QName qname = null;
   			boolean bRPC = false;
   			String style = operation.getStyle();
   			if (style.toUpperCase().equals("RPC")) bRPC = true;
   			
   			// Set SOAP response element
   			if (bRPC) {
   				try {
					MessagePart[] parts = operation.getDefaultResponseParts();
					if (parts.length>0) {
						String ename = parts[0].getName();
						if (parts[0].getPartType().name().equals("CONTENT")) {
							MessagePart.ContentPart mpcp = (MessagePart.ContentPart)parts[0];
							qname = mpcp.getSchemaType().getName();
							if (qname != null) {
								// response is based on an element defined with a type
								// operationResponse element name; element name; element type
								String responseQName = operationName + "Response;" + ename + ";" + "{"+qname.getNamespaceURI()+"}" + qname.getLocalPart();
								xmlHttpTransaction.setResponseElementQName(responseQName);
							}
						}
					}
   				}
   				catch (Exception e) {}
   			}
   			else {
				try {
					qname = operation.getResponseBodyElementQName();
					if (qname != null) {
						QName refName = new QName(qname.getNamespaceURI(),qname.getLocalPart());
						xmlHttpTransaction.setXmlElementRefAffectation(new XmlQName(refName));
					}
				}
				catch (Exception e) {}
			}
			
   			// Create request/response
		   	request = operation.addNewRequest("Test"+transactionName);
		   	requestXml = operation.createRequest(true);
		   	request.setRequestContent(requestXml);
		   	//responseXml = operation.createResponse(true);
		   	
		   	
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware( true );
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document requestDoc = db.parse(new InputSource(new StringReader(requestXml)));
			//Document responseDoc = db.parse(new InputSource(new StringReader(responseXml)));
		   	
			Element enveloppe = requestDoc.getDocumentElement();
			String soapenvNamespace = enveloppe.getNamespaceURI();
			
			// Retrieve variables
		   	Element header = (Element)requestDoc.getDocumentElement().getElementsByTagNameNS(soapenvNamespace,"Header").item(0);
			Element body = (Element)requestDoc.getDocumentElement().getElementsByTagNameNS(soapenvNamespace,"Body").item(0);
			
			//System.out.println(XMLUtils.prettyPrintDOM(requestDoc));
			
			// Extract variables
			extractVariables(xmlSchema, variables, header, null, false, null);
			extractVariables(xmlSchema, variables, body, null, false, null);
			
			// Serialize request/response into template xml files
			String connectorName = httpConnector.getName();
			String templateDir = Engine.PROJECTS_PATH + "/"+ projectName + "/soap-templates/" + connectorName;
			File dir = new File(templateDir);
			if (!dir.exists())
				dir.mkdirs();
			
			String requestTemplateName = "/soap-templates/" + connectorName + "/" + xmlHttpTransaction.getName() + ".xml";
			String requestTemplate = Engine.PROJECTS_PATH + "/"+ projectName + requestTemplateName;
			//String responseTemplateName = "/soap-templates/" + connectorName + "/" + xmlHttpTransaction.getName() + "-Response.xml";
			//String responseTemplate = Engine.PROJECTS_DIRECTORY + "/"+ projectName + responseTemplateName;
			
			xmlHttpTransaction.setRequestTemplate(requestTemplateName);
			saveTemplate(requestDoc, requestTemplate);
			//createTemplate(responseDoc, responseTemplate);
			
   			// Adds variables
   			for (RequestableHttpVariable variable: variables) {
   				//System.out.println("adding "+ variable.getName());
   				xmlHttpTransaction.add(variable);
   			}
			
			xmlHttpTransaction.hasChanged = true;
	   	}
		
		return xmlHttpTransaction;
	}
	
	private void saveTemplate(Document doc, String templateDir) throws EngineException {
		setTaskLabel("Creating template \""+templateDir+"\"...");
		try {
            Source source = new DOMSource(doc);
            File file = new File(templateDir);
            Result result = new StreamResult(new FileOutputStream(file));
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(source, result);
        } catch (Exception e) {
        	throw new EngineException("Unable to create template file \""+templateDir+"\"", e);
        }
	}

	private void extractVariables(XmlSchema xmlSchema, List<RequestableHttpVariable> variables, Node node, String longName, boolean isMulti, QName variableType) throws EngineException {
		if (node == null) return;
		int type = node.getNodeType();
		
		if (type == Node.ELEMENT_NODE) {
			Element element = (Element)node;
			if (element != null) {
				String elementName = element.getLocalName();
				if (longName != null)
					elementName = longName + "_" + elementName;
				
				if (!element.getAttribute("soapenc:arrayType").equals("") && !element.hasChildNodes()) {
					String avalue = element.getAttribute("soapenc:arrayType");
					element.setAttribute("soapenc:arrayType", avalue.replaceAll("\\[\\]", "[1]"));
					
					Element child = element.getOwnerDocument().createElement("item");
					String atype = avalue.replaceAll("\\[\\]", "");
					child.setAttribute("xsi:type", atype);
					if (atype.startsWith("xsd:")) {
						String variableName = elementName + "_item";
						child.appendChild(element.getOwnerDocument().createTextNode("$("+ variableName.toUpperCase() +")"));
						RequestableHttpVariable httpVariable = createVariable(true, variableName, new QName(Constants.URI_2001_SCHEMA_XSD,atype.split(":")[1]));
						variables.add(httpVariable);
					}
					element.appendChild(child);
				}
				
				// extract from attributes
				NamedNodeMap map = element.getAttributes();
				for (int i=0; i<map.getLength(); i++) {
					Node child = map.item(i);
					if (child.getNodeName().equals("soapenc:arrayType")) continue;
					if (child.getNodeName().equals("xsi:type")) continue;
					if (child.getNodeName().equals("soapenv:encodingStyle")) continue;
					
					String variableName = getVariableName(variables, elementName + "_" + child.getLocalName());
					
					child.setNodeValue("$("+ variableName.toUpperCase() +")");
					
					setTaskLabel("Creating variable \""+variableName+"\"...");
					RequestableHttpVariable httpVariable = createVariable(false, variableName, Constants.XSD_STRING);
					variables.add(httpVariable);
				}
				
				// extract from children nodes
				boolean multi = false;
				QName qname = Constants.XSD_STRING;
				NodeList children = element.getChildNodes();
				if (children.getLength() > 0) {
					Node child = element.getFirstChild();
					while (child != null) {
						if (child.getNodeType() == Node.COMMENT_NODE) {
							String value = child.getNodeValue();
							if (value.startsWith("type:")) {
								String schemaType = child.getNodeValue().substring("type:".length()).trim();
								qname = getVariableSchemaType(xmlSchema, schemaType);
							}
							if (value.indexOf("repetitions:") != -1) {
								multi = true;
							}
						}
						else if (child.getNodeType() == Node.TEXT_NODE) {
							String value = child.getNodeValue().trim();
							if (value.equals("?") || !value.equals("")) {
								String variableName = getVariableName(variables, elementName);
								
								child.setNodeValue("$("+ variableName.toUpperCase() +")");
								
								setTaskLabel("Creating variable \""+variableName+"\"...");
								RequestableHttpVariable httpVariable = createVariable(isMulti, variableName, variableType);
								variables.add(httpVariable);
							}
						}
						else if (child.getNodeType() == Node.ELEMENT_NODE) {
							extractVariables(xmlSchema, variables, child, elementName, multi, qname);
							multi = false;
							qname = Constants.XSD_STRING;
						}
						
						child = child.getNextSibling();
					}
				}
				
			}
		}
	}
	
	private static String getVariableName(List<RequestableHttpVariable> variables, String name) {
		String variableName = StringUtils.normalize(name);
		int index = 1;
		for (RequestableHttpVariable variable: variables) {
			if (variable.getName().equals(variableName)) {
				variableName += "_"+ index;
				index++;
				continue;
			}
		}
		return variableName;
	}
	
	private static QName getVariableSchemaType(XmlSchema xmlSchema, String schemaType) {
		String local = "string";
		String nsuri = Constants.URI_2001_SCHEMA_XSD;
		try {
			if (!schemaType.equals("")) {
				if (schemaType.startsWith("{")) {
					int i = schemaType.indexOf("}");
					if (i  != -1) {
						nsuri = schemaType.substring(1, i);
						local = schemaType.substring(i+1);
						int j = local.indexOf(" - "); // there's an info
						if (j != -1) {
							local = local.substring(0, j);
						}
					}
				}
				else if (schemaType.indexOf(":") != -1) {
					String[] qinfos = schemaType.split(":");
					nsuri = xmlSchema.getNamespaceContext().getNamespaceURI(qinfos[0]);
					local = qinfos[1];
				}
			}
		
			QName qname = new QName(nsuri,local);
			return qname;
		}
		catch (Exception e) {
			return Constants.XSD_STRING;
		}
	}
	
	private static RequestableHttpVariable createVariable(boolean multi, String variableName, QName schemaTypeName) throws EngineException {
		RequestableHttpVariable httpVariable = (multi ? new RequestableHttpMultiValuedVariable():new RequestableHttpVariable());
		httpVariable.setName(variableName);
		httpVariable.setDescription(variableName);
		httpVariable.setWsdl(Boolean.TRUE);
		httpVariable.setPersonalizable(Boolean.FALSE);
		httpVariable.setCachedKey(Boolean.TRUE);
		httpVariable.setHttpMethod("POST");
		httpVariable.setHttpName(variableName.toUpperCase());
		httpVariable.setXmlTypeAffectation(new XmlQName(schemaTypeName));
		httpVariable.bNew = true;
		return httpVariable;
	}
	
}
