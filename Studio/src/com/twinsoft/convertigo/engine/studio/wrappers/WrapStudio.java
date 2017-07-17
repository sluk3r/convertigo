package com.twinsoft.convertigo.engine.studio.wrappers;

import java.util.List;

import com.twinsoft.convertigo.beans.core.DatabaseObject;

public interface WrapStudio {

	List<WrapObject> getSelectedObjects();
	WrapObject getFirstSelectedTreeObject();
	Object getFirstSelectedDatabaseObject();
	void addSelectedObject(DatabaseObject dbo);
	void addSelectedObjects(DatabaseObject ...dbos);

	int openMessageDialog(String title, Object object, String msg, String string, String[] buttons, int defaultIndex);
	int openMessageBox(String title, String msg, String[] buttons);

	void setResponse(int reponse);	
	boolean isActionDone();
}
