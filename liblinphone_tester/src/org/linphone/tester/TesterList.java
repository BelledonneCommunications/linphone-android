package org.linphone.tester;

import java.util.LinkedList;
import java.util.List;

public class TesterList extends Tester {
	private List<String> list = new LinkedList<String>();
	public void printLog(final int level, final String message) {
		super.printLog(level, message);
		list.add(message);
	}

	public List<String> getList() {
		return list;
	}
}
