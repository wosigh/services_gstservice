/*
	CommandLine.java
	=-=-=-=-=-=-=-=-
	Jason Robitaille  November 15, 09
	MIT License
*/

package org.webosinternals.gstservice;

import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;

public class CommandLine {
	private final String SCRIPTFILE = "/var/gstScript.sh";
	private ArrayList<String> cmds; //list of commands
	private String response; //response
	private int returnCode;

	public CommandLine() {
		cmds = new ArrayList<String>();
		response = null;
	}

	public void addCmd(String line) { cmds.add(line); }

	public String getCmd(int i) { return cmds.get(i); }

	public void removeCmd(int i) { cmds.remove(i); }

	public int cmdLineCount() { return cmds.size(); }

	public void clear(int i) { cmds.clear(); }

	public int getReturnCode() { return returnCode; }

	public String getResponse() { return response; }

	public boolean run() {
		boolean success = false;
		File output = createScript();
		if(output!=null)
			success = runScript(output);
		return success;
	}
	private File createScript() {
		File result = new File(SCRIPTFILE);
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(result));
			bw.write("#!/bin/sh\n");
			for(int i=0; i<cmds.size(); i++) {
				bw.write(cmds.get(i) + "\n");
			}
			bw.write("exit 0\n");
			bw.flush();
			bw.close();
		} catch(Exception e) {
			response = e.getMessage();
			result = null;
		}
		return result;
	}
	private boolean runScript(File script) {
		boolean success = false;
		try {
			Process p = Runtime.getRuntime().exec("/bin/sh " + SCRIPTFILE);
			BufferedReader input = new BufferedReader(
					new InputStreamReader(p.getInputStream()));
			String line = null;
			String output = "";
			line = input.readLine();
			while (line!=null) {
				output += line;
				line = input.readLine();
				if(line!=null) {
					output += " ";
				}
			}
			response = output;
			returnCode = p.waitFor();
			if(returnCode==0) {
				success = true;
			}
		} catch(Exception e) {
			response = e.getMessage();
		}
		script.delete();
		return success;
	}

}
