package p2p;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;

/**
 * 中央服务器 保存每个peer的文件信息
 * 
 * @author michael
 * 
 */
public class Center {

	private static DatagramSocket mSocket;
	private static final int PORT = 9090;
	private static InetAddress sendAddr;// 发送方地址
	private static int sendPort;// 发送方端口
	private final int bufSize = 65536;

	private ArrayList<FileInfo> fIList = new ArrayList<FileInfo>();// 文件信息链表
	
	private String fileInfoPath = "file.in";//文件信息路径
	private FileWriter fileWriter;

	/**
	 * 文件信息类
	 * 
	 * @author michael
	 * 
	 */
	class FileInfo {
		String host;
		int port;
		String url;

		public FileInfo(String host, int port, String url) {
			this.host = host;
			this.port = port;
			this.url = url;
		}
	}

	public Center() {
		System.out.println("=========中央服务器启动==========");
		try {
			//读入已存在的文件信息
			File file = new File(fileInfoPath);
			if (!file.exists()) {
				file.createNewFile();
			}else {
				readFileInfo(file);
			}
			fileWriter = new FileWriter(file, true);//append的方式打开
			mSocket = new DatagramSocket(PORT);
			ServerThread thread = new ServerThread(mSocket);
			thread.start();
		} catch (SocketException e) {
			// TODO: handle exception
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 读取文件信息
	 * @param file
	 */
	private void readFileInfo(File file){
		try {
			BufferedReader bufReader = new BufferedReader(new FileReader(file));
			String line;
			int index1,index2;
			String host,path;
			int port;
			while((line=bufReader.readLine()) != null) {
				index1 = line.indexOf(" ");
				index2 = line.indexOf(" ", index1 + 1);
				host = line.substring(0, index1);
				port = Integer.valueOf(line.substring(index1 + 1, index2));
				path = line.substring(index2 + 1);
				fIList.add(new FileInfo(host, port, path));
			}
			bufReader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 解析接收到的消息
	 * 
	 * @param iBuf
	 * @param iLen
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public void dataReceived(byte[] iBuf, int iLen) {
		DatagramPacket sPacket;
		try {
			String msg = new String(iBuf).trim();
			// 解析信息类型
			if (msg.startsWith("Filelist")) {// peer发来文件列表
				String peerName = getNameByMsg(msg);
				System.out.println(peerName + "发来文件信息");
				
				msg = msg.substring(msg.indexOf(":") + 1);
				String separator = "$#$";
				while(!msg.equals("")) {
					int index = msg.indexOf(separator);
					String path = msg.substring(0, index);
					fIList.add(new FileInfo(sendAddr.getHostAddress(), sendPort, path));
					//将文件信息写入磁盘
					fileWriter.write(sendAddr.getHostAddress() + " " + sendPort + " " + path + "\n");
					System.out.println(path);
					msg = msg.substring(index + 3);
				}
				fileWriter.flush();
			} else {// peer请求文件信息
				String res = "没有文件";
				byte[] bSend;
				if (fIList.isEmpty()) {
					bSend = res.getBytes();
					sPacket = new DatagramPacket(bSend, bSend.length, sendAddr,
							sendPort);
					mSocket.send(sPacket);
					return;
				}
				res = "";
				for (int i = 0; i < fIList.size(); i++) {
					res += (i + 1) + ".<" + fIList.get(i).url + ","
							+ fIList.get(i).host + "," + fIList.get(i).port
							+ ">\n";
				}
				bSend = res.getBytes();
				sPacket = new DatagramPacket(bSend, bSend.length, sendAddr,
						sendPort);
				mSocket.send(sPacket);
			}

		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	/**
	 * 从消息中读取Peer的名字
	 * @return
	 */
	private String getNameByMsg(String msg) {
		int index = msg.indexOf("From");
		msg = msg.substring(index + 1);
		index = msg.indexOf(" ");
		int index2 = msg.indexOf(":", index + 1);
		return msg.substring(index+1, index2);
	}

	private class ServerThread extends Thread {

		private DatagramSocket mSocket;

		public ServerThread(DatagramSocket socket) {
			mSocket = socket;
		}

		@Override
		public void run() {
			byte buf[] = new byte[bufSize];
			DatagramPacket rPacket = new DatagramPacket(buf, bufSize);
			try {
				for (;;) {
					for (int i = 0; i < buf.length; i++) {
						buf[i] = 0;
					}
					mSocket.receive(rPacket);
					// 根据接收到的数据包获取发送方的地址和端口
					sendAddr = rPacket.getAddress();
					sendPort = rPacket.getPort();
					dataReceived(buf, buf.length);
				}
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String args[]) {
		new Center();
	}
}
