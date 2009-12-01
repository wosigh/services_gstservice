package org.webosinternals.gstservice;

import com.palm.luna.LSException;
import com.palm.luna.service.LunaServiceThread;
import com.palm.luna.service.ServiceMessage;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;


public class GstService extends LunaServiceThread {
	
	private final String[][] AUDIOOPTIONS = new String[][] {
			new String[] {"AAC", "AMRNB", "MP3"},
			new String[] {"0", "1", "2"}};
	private final String[][] VIDEOOPTIONS = new String[][] {
			new String[] {"MPEG-4", "H.263", "H.264/AVC"},
			new String[] {"0", "1", "2"}};
	private final String[][] MUXOPTIONS = new String[][] {
			new String[] {"mp4", "3gp"},
			new String[] {"TRUE", "FALSE"}};
	private final String[][] STREAMOPTIONS = new String[][] {
			new String[] {"audio", "video", "both"},
			new String[] {"2", "1", "0"}};
	private final String[][] BRIGHTNESSOPTIONS = new String[][] {
			new String[] {"off", "low", "medium", "high"},
			new String[] {"0", "50", "100", "150"}};
	private String hwVersion;
	private String currOutput;
	//private String useFlash;
	private CommandLine cmd;
	
	public GstService() {
		this.hwVersion = "0.3.1";
		cmd = null;
		currOutput = null;
	}

	@LunaServiceThread.PublicMethod
	public void version(ServiceMessage message) throws LSException {
		StringBuilder sb = new StringBuilder(8192);
		sb.append("{version:");
		sb.append(JSONObject.quote(this.hwVersion));
		sb.append("}");
		message.respond(sb.toString());
	}
	
	@LunaServiceThread.PublicMethod
	public void status(ServiceMessage msg) throws JSONException, LSException {
			JSONObject reply = new JSONObject();
		reply.put("returnValue",true);
		msg.respond(reply.toString());
	}

	@LunaServiceThread.PublicMethod
	public void videoRec(ServiceMessage msg) throws JSONException, LSException {
			JSONObject jsonParam = msg.getJSONPayload();
		if(jsonParam.has("filename")) {
			String audio, video, container, stream, brightness;
			audio = getParam(jsonParam, "audio", "0", AUDIOOPTIONS);
			video = getParam(jsonParam, "video", "2", VIDEOOPTIONS);
			container = getParam(jsonParam, "container", "TRUE", MUXOPTIONS);
			stream = getParam(jsonParam, "stream", "0", STREAMOPTIONS);
			brightness = getParam(jsonParam, "brightness", "0", BRIGHTNESSOPTIONS);
			currOutput = formatName(jsonParam.getString("filename"), container);
			cmd = new CommandLine();
			cmd.addCmd(buildGstCall(audio, video, container, stream));
			JSONObject reply = new JSONObject();
			reply.put("returnValue", true);
			msg.respond(reply.toString());
			cmd.run();
			cmd = new CommandLine();
			cmd.addCmd("sleep 1");
			cmd.addCmd("echo -n 1 >/sys/class/i2c-adapter/i2c-2/2-0033/avin");
			cmd.addCmd("echo -n " + brightness + "mA >/sys/class/i2c-adapter/i2c-2/2-0033/torch_current");
			cmd.addCmd("echo -n torch >/sys/class/i2c-adapter/i2c-2/2-0033/mode");
			cmd.run();
			}
		else {
			msg.respondError("1", "Service request missing output filename.");
		}
	}
	
	private String getParam(JSONObject json, String key, String plain, String[][] data)
			throws JSONException {
		String result = null;
		if(json.has(key)) {
			String item = json.getString(key);
			for(int i=0; i<data[0].length; i++) {
				if(data[0][i].equalsIgnoreCase(item)) {
					result = data[1][i];
					break;
				}
			}
		}
		if(result==null)
			result = plain;
		return result;
	}
	
	private String formatName(String name, String container) {
		boolean isMP4 = Boolean.parseBoolean(container.toLowerCase());
		String result = name;
		if(isMP4) {
			if(!result.endsWith(".mp4")) {
				result += ".mp4";
			}
		} else {
			if(!result.endsWith(".3gp")) {
				result += ".3gp";
			}
		}
		return result;
	}
	
	private String buildGstCall(String audio, String video, String mux, String stream) {
		return "/var/usr/palm/applications/org.webosinternals.gstservice/bin/gst-launch -e camsrc ! palmvideoencoder videoformat=" + video
				+ " ! palmmpeg4mux name=mux QTQCELPMuxing=" + mux + " StreamMuxSelection="
				+ stream + " enable=true alsasrc ! queue ! palmaudioencoder encoding="
				+ audio + " enable=true ! mux.";
	}
	
	@LunaServiceThread.PublicMethod
	public void videoStop(ServiceMessage msg) throws JSONException, LSException {
		if(currOutput!=null) { //a recording is in process
			currOutput = cmd.getResponse();
			cmd = new CommandLine();
			cmd.addCmd("echo -n shutdown >/sys/class/i2c-adapter/i2c-2/2-0033/mode");
			cmd.addCmd("echo -n 0mA >/sys/class/i2c-adapter/i2c-2/2-0033/torch_current");
			cmd.addCmd("echo -n 0 >/sys/class/i2c-adapter/i2c-2/2-0033/avin");
			cmd.run();
			cmd = new CommandLine();
			cmd.addCmd("pkill -SIGINT gst-launch");
			cmd.addCmd("sleep 3");
			cmd.addCmd("pkill -SIGINT gst-launch");
			cmd.addCmd("pkill -SIGINT camd");
			cmd.run();
			JSONObject reply = new JSONObject();
			reply.put("output", currOutput);
			reply.put("path", relocateVideo());
			msg.respond(reply.toString());
		}
	}
	
	private String relocateVideo() {
		File baseDir = new File("/media/internal/");
		File[] files = baseDir.listFiles();
		File newest = null;
		File destDir = new File("/media/internal/video/");
		if(!destDir.isDirectory()) {
			destDir.mkdirs();
		}
		File dest = new File(destDir, currOutput);
		//newest file will be the one just created by gstreamer
		for(int i=0; i<files.length; i++) {
			if(files[i].isFile()) {
				if(newest==null) {
					newest = files[i];
				} else {
					if(newest.lastModified()<files[i].lastModified()) {
						newest = files[i];
					}
				}
			}
		}
		if(newest!=null) {
			cmd = new CommandLine();
			cmd.addCmd("mv " + newest + " " + dest + "/" + currOutput +"");
			cmd.run();
		}
		String newestfn = newest.getName();
		return newestfn;
	}
	
	/*private String updateFileIndexer() {
		CommandLine forceRescan = new CommandLine();
		forceRescan.addCmd("/sbin/stop fileindexer");
		forceRescan.addCmd("/bin/rm -f /var/luna/data/mediadb.db3");
		forceRescan.addCmd("/sbin/start fileindexer");
		forceRescan.run();
		return forceRescan.getResponse();
	}*/
}
