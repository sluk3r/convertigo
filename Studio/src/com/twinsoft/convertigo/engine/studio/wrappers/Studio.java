package com.twinsoft.convertigo.engine.studio.wrappers;

import java.util.ArrayList;
import java.util.List;

import com.twinsoft.convertigo.beans.core.Connector;
import com.twinsoft.convertigo.beans.core.DatabaseObject;
import com.twinsoft.convertigo.beans.core.Project;
import com.twinsoft.convertigo.beans.core.Sequence;
import com.twinsoft.convertigo.beans.core.Transaction;

public abstract class Studio implements WrapStudio {

	private List<WrapObject> selectedObjects = new ArrayList<>();
	protected boolean isActionDone = false;
	protected int response;

	@Override
	public List<WrapObject> getSelectedObjects() {
		return selectedObjects;
	}

	@Override
	public WrapObject getFirstSelectedTreeObject() {
	    return getSelectedObjects().isEmpty() ? null : getSelectedObjects().get(0);
	}

    public Object getFirstSelectedDatabaseObject() {
        Object object = null;
        WrapDatabaseObject treeObject = (WrapDatabaseObject) getFirstSelectedTreeObject();
        if (treeObject != null) {
            object = treeObject.getObject();
        }
        return object;
    }

	@Override
	public void addSelectedObject(DatabaseObject dbo) {
		selectedObjects.add(getViewFromDbo(dbo, this));
	}

	public static DatabaseObjectView getViewFromDbo(DatabaseObject dbo, WrapStudio studio) {
        if (dbo instanceof Project) {
            return new ProjectView((Project) dbo, studio); 
        }
        if (dbo instanceof Connector) {
            return new ConnectorView((Connector) dbo, studio); 
        }
        if (dbo instanceof Sequence) {
            return new SequenceView((Sequence) dbo, studio); 
        }
        if (dbo instanceof Transaction) {
            return new TransactionView((Transaction) dbo, studio); 
        }

        return new DatabaseObjectView(dbo, studio);
	}

	@Override
	public void addSelectedObjects(DatabaseObject ...dbos) {
		for (DatabaseObject dbo: dbos) {
			addSelectedObject(dbo);
		}
	}

	@Override
	public void setResponse(int response) {
		synchronized (this) {
			this.response = response;
			notify();
		}
	}

	@Override
	public boolean isActionDone() {
		return isActionDone;
	}
}
