package p2p;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

public class Peer {

	private String name;

	private int mPort;
	private ServerSocket mSS;
	private Socket mS;
	private DatagramSocket mSocket;

	// 中央服务器的信息
	private InetAddress serverAddr;
	private int serverPort = 9090;

	private int bufferSize = 8192;
	private final int MAX_LEN = 65536;

	private String fileDir = "Files/";
	private String lgin = "login";
	private String rquest = "reqFiles";
	private String fileDeleted = "该文件已经被删除了";
	private String HELP = "提示：d下载文件，q退出.";
	
	private Scanner sc = new Scanner(System.in);

	public Peer(String name) throws Exception {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	private void showHelp() {
		System.out.println(HELP);
	}

	public void run() {
		try {
			serverAddr = InetAddress.getByName("127.0.0.1");
			// 随机生成一个端口
			Random random = new Random();
			mPort = random.nextInt(64512) + 1024;
			mSocket = new DatagramSocket(mPort);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		//向中央服务器发送本地的文件列表
		sendFileList();
		
		// 启动服务器
		startServer();

		// 进行其他操作
		action();
	}
	
	/**
	 * 向中央服务器发送本地的文件列表
	 */
	public void sendFileList() {
		try {
			String msg = "Filelist From " + name + ":";
			File file = new File(fileDir);
			String parent = file.getAbsolutePath() + "/";
			String[] list = file.list();
			for (int i = 0; i < list.length; i++) {
				msg += parent + list[i] + "$#$";//以"$#$"隔开各文件路径
			}
			byte[] bb = msg.getBytes();
			DatagramPacket sPacket = new DatagramPacket(bb, bb.length,
				serverAddr, serverPort);
			mSocket.send(sPacket);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	public void startServer() {
		//启动服务线程
		ServerThread thread = new ServerThread();
		thread.start();
	}
	
	public class ServerThread extends Thread {
		
		public void run() {
			try {
				mSS = new ServerSocket(mPort);
				while (true) {
					mS = mSS.accept();
					uploadFile(mS);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void action() {
		showHelp();// 显示帮助信息
		while (true) {
			System.out.print(">>");
			String text = sc.next();
			if (text.equals("q")) {
				System.exit(0);
			} else if (text.equals("d")) {// 下载文件
				// 请求所有文件信息
				String msg = reqFiles();
				if (msg == null || msg.equals("没有文件")) {
					return;
				}
				System.out.println(msg);
				System.out.print("请选择文件序号:");
				int seq = sc.nextInt();
				// 解析获取文件所在的主机和路径
				int index = msg.indexOf("\n" + seq);
				while (index == -1) {
					System.out.println("输入无效,再次输入:");
					seq = sc.nextInt();
				}
				msg = msg.substring(index);
				int index1 = msg.indexOf("<");
				int index2 = msg.indexOf(",");
				int index3 = msg.indexOf(",", index2 + 1);
				int index4 = msg.indexOf(">");
				String url = msg.substring(index1 + 1, index2);
				System.out.println(url);
				String host = msg.substring(index2 + 1, index3);
				int port = Integer.valueOf(msg.substring(index3 + 1, index4));
				downloadFile(host, port, url);
			}
		}
	}

	/**
	 * 向中央服务器请求文件信息
	 * 
	 * @return
	 */
	public String reqFiles() {
		String recvMsg = null;
		try {
			byte[] bb = rquest.getBytes();
			DatagramPacket sPacket = new DatagramPacket(bb, bb.length,
					serverAddr, serverPort);
			mSocket.send(sPacket);
			// 接收服务器回发的信息
			byte[] recvBuf = new byte[MAX_LEN];
			DatagramPacket rPacket = new DatagramPacket(recvBuf, MAX_LEN);
			mSocket.receive(rPacket);
			recvMsg = new String(recvBuf).trim();// 去除字符串前面和后面的空格
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return recvMsg;
	}

	/**
	 * 上传文件到服务器(实际上是发送文件路径)
	 * @param socket
	 * @throws IOException
	 */
	public void uploadFile(Socket socket) {
		try {
			DataInputStream dis = new DataInputStream(new BufferedInputStream(
					socket.getInputStream()));
			// 读取要发送的文件路径
			String url = dis.readUTF();
			// 发送文件
			File file = new File(url);
			DataInputStream fileDis = new DataInputStream(
					new BufferedInputStream(new FileInputStream(file)));
			DataOutputStream dos = new DataOutputStream(
					socket.getOutputStream());
			if (!file.exists()) {
				System.out.println("文件不存在");
				dos.writeUTF(fileDeleted);
				dos.flush();
				return;
			}
			sendFile(fileDis, dos, file);
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	private void sendFile(DataInputStream dis, DataOutputStream dos, File file) {
		try {
			// 将文件名及长度发给服务器
			dos.writeUTF(file.getName());
			dos.flush();
			dos.writeLong((long) file.length());
			dos.flush();

			byte[] buf = new byte[bufferSize];
			int read = 0;

			while (true) {
				read = dis.read(buf);
				if (read == -1) {
					break;
				}
				dos.write(buf, 0, read);
			}
			dos.flush();
			// 注意关闭socket链接，不然客户端会等待server的数据过来，
			// 直到socket超时，导致数据不完整。
			dis.close();
			dos.close();
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}

	/**
	 * 从其他peer下载文件
	 */
	public void downloadFile(String host, int port, String url) {
		try {
			Socket socket = new Socket(InetAddress.getByName(host), port);
			DataInputStream dis = new DataInputStream(socket.getInputStream());
			DataOutputStream dos = new DataOutputStream(
					socket.getOutputStream());
			// 发送文件路径
			dos.writeUTF(url);
			dos.flush();

			System.out.print("输入文件的保存位置(绝对路径):");
			String savePath = sc.next();
			recvFile(dis, dos, savePath);
		} catch (Exception e) {
			// TODO: handle exception
//			e.printStackTrace();
			System.out.println("该用户不在线，向其他用户请求文件吧！");
		}
	}

	private void recvFile(DataInputStream dis, DataOutputStream dos,
			String savePath) {
		try {
			byte[] buf = new byte[bufferSize];
			long len = 0;

			String fileName = dis.readUTF();
			//判断发过来的信息是不是文件名
			if(fileName.equals(fileDeleted)) {
				System.out.println(fileDeleted);
				return;
			}
			
			if (!savePath.endsWith("/")) {
				savePath += "/";
			}
			savePath += fileName;
			dos = new DataOutputStream(new BufferedOutputStream(
					new BufferedOutputStream(new FileOutputStream(savePath))));
			len = dis.readLong();

			System.out.println("文件的长度为:" + len);

			int read = 0;
			while (true) {
				read = dis.read(buf);
				if (read == -1) {
					break;
				}
				dos.write(buf, 0, read);
			}
			System.out.println("接收完成，文件存为" + savePath);
			dis.close();
			dos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		Scanner sc = new Scanner(System.in);
		// 创建一个peer并登陆
		System.out.print("输入用户名登录：");// 我省略了注册，直接输入名字登录
		String text = sc.next();
		Peer peer = new Peer(text);
		
//		while (!peer.login()) {
//			text = sc.next();
//			peer = new Peer(text);
//		}
		
		//以ip+port区别每一个peer
		peer.run();
	}
}
