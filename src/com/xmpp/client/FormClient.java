package com.xmpp.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import com.xmpp.client.util.TimeRender;
import com.xmpp.client.util.XmppTool;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
/**
 * 发送文本和文件的聊天页面
 * @author chunjiang.shieh
 *
 */
public class FormClient extends Activity {

	private static final String TAG = FormClient.class.getSimpleName();
	
	//服务器名称（域名）
	private static final String SERVER_NAME = "@xcjxmppserver";
	//Xmpp 资源名称（比如连接的客户端名称等等） 自己自定义
	private static final String XMPP_RESOURCES = "Android";
	
//	private String FROM_USERNAME = "laiyue";   //参与者
	private String FROM_USERNAME = "xiechunjiang";   //参与者

	
	private MyAdapter adapter;
	private List<Msg> listMsg = new ArrayList<Msg>();
	private String pUSERID;
	private EditText msgText;
	private ProgressBar pb;		//接收文件时的进度条

	/**
	 * 发送文本消息的模型类
	 * @author chunjiang.shieh
	 *
	 */
	public class Msg {
		String userid;  //消息的所属者
		String msg;		//发送的消息内容
		String date;		//发送的时间
		String from;		//IN 代表接收的消息 OUT 代表发送的消息

		public Msg(String userid, String msg, String date, String from) {
			this.userid = userid;
			this.msg = msg;
			this.date = date;
			this.from = from;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.formclient);
		this.pUSERID = getIntent().getStringExtra("USERID");
		initView();

		//message listener 得到一个会话管理的实例
		ChatManager cm = XmppTool.getConnection().getChatManager();
		//从会话管理实例中创建一个会话
		final Chat newchat = cm.createChat(FROM_USERNAME + SERVER_NAME, null);
//		final Chat newchat = cm.createChat(TO_USERNAME, null);
		//添加一个聊天的监听
		cm.addChatListener(new ChatManagerListener() {
			@Override
			public void chatCreated(Chat chat, boolean able) {
				chat.addMessageListener(new MessageListener() {
					@Override
					public void processMessage(Chat chat2, Message message) {//接收到消息
						//message from user [test2@sam]
						Log.d(TAG, "---------->processMessage from: "
								+message.getFrom() 
								+" body: "+message.getBody());
						
						if(message.getFrom().contains(FROM_USERNAME)){
							String[] args = new String[] { FROM_USERNAME, message.getBody(), TimeRender.getDate(), "IN" };
							android.os.Message msg = handler.obtainMessage();
							msg.what = MSG_TEXT_RECEIVED_SUCCESS;
							msg.obj = args;
							msg.sendToTarget();
						}else{
							// orther user / group / admin of the openfire
							// do work...
						}
					}
				});
			}
		});

		//send file
		Button btattach = (Button) findViewById(R.id.formclient_btattach);
		btattach.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(FormClient.this, FormFiles.class);
				startActivityForResult(intent, 2);				
			}			
		});
		//send message
		Button btsend = (Button) findViewById(R.id.formclient_btsend);
		btsend.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String msg = msgText.getText().toString();
				if(msg.length() > 0){
					listMsg.add(new Msg(pUSERID, msg, TimeRender.getDate(), "OUT"));
					adapter.notifyDataSetChanged();
					try {
						Log.d(TAG, "-------->send msg: "+msg);
						newchat.sendMessage(msg);
						
//						Message message = new Message(TO_USERNAME + "@tgramserver");
//						message.setBody(msg);
//						message.setType(Message.Type.chat);
//						newchat.sendMessage(message);
					} catch (XMPPException e) {
						e.printStackTrace();
					}
				}
				msgText.setText("");
			}
		});
		//receive file (文件传输的管理者)
		FileTransferManager fileTransferManager = new FileTransferManager(XmppTool.getConnection());
		//添加File传输的事件监听
		fileTransferManager.addFileTransferListener(new RecFileTransferListener());
	}


	private void initView() {
		ListView listview = (ListView) findViewById(R.id.formclient_listview);
		listview.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		this.adapter = new MyAdapter(this);
		listview.setAdapter(adapter);
		this.msgText = (EditText) findViewById(R.id.formclient_text);
		this.pb = (ProgressBar) findViewById(R.id.formclient_pb);
	}

	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode==2 && resultCode==2 && data!=null){
			String filepath = data.getStringExtra("filepath");
			Log.d(TAG, "filepath: "+filepath);
			if(filepath.length() > 0){
				sendFile(filepath);
			}
		}
	}
	
	/**
	 * 根据文件路径发送文件
	 * @param filepath
	 */
	private void sendFile(String filepath) {
		// ServiceDiscoveryManager sdm = new ServiceDiscoveryManager(connection);
		final FileTransferManager fileTransferManager = new FileTransferManager(XmppTool.getConnection());
		final OutgoingFileTransfer fileTransfer = fileTransferManager.createOutgoingFileTransfer(FROM_USERNAME+SERVER_NAME
				+"/"+XMPP_RESOURCES);				
		final File file = new File(filepath);
		try {
			Log.d(TAG, "-------->sendFile");
			fileTransfer.sendFile(file, "Sending");
		} catch (Exception e) {
			e.printStackTrace();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try{					
					while (true) {
						Thread.sleep(500L);
						Status status = fileTransfer.getStatus();								
						if ((status == FileTransfer.Status.error)
								|| (status == FileTransfer.Status.complete)
								|| (status == FileTransfer.Status.cancelled)
								|| (status == FileTransfer.Status.refused)) {
							handler.sendEmptyMessage(MSG_PROGRESSBAR_GONE);
							break;
						}else if(status == FileTransfer.Status.negotiating_transfer){
							//..
						}else if(status == FileTransfer.Status.negotiated){							
							//..
						}else if(status == FileTransfer.Status.initial){
							//..
						}else if(status == FileTransfer.Status.negotiating_stream){							
							//..
						}else if(status == FileTransfer.Status.in_progress){
							handler.sendEmptyMessage(MSG_PROGRESSBAR_VISIBLE);
							long p = fileTransfer.getBytesSent() * 100L / fileTransfer.getFileSize();													
							android.os.Message message = handler.obtainMessage();
							message.arg1 = Math.round((float) p);
							message.what = MSG_PROGRESSBAR_UPDATING;
							message.sendToTarget();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	//文件接收的请求（reject或者accept）
	private FileTransferRequest request;
	private File file;

	/**
	 * 处理文件的接收
	 * @author chunjiang.shieh
	 *
	 */
	class RecFileTransferListener implements FileTransferListener {
		@Override
		public void fileTransferRequest(FileTransferRequest prequest) {
			Log.d(TAG,"The file received from: " + prequest.getRequestor());
			file = new File("mnt/sdcard/" + prequest.getFileName());
			request = prequest;
			handler.sendEmptyMessage(MSG_FILE_RECEIVED_SUCCESS);
		}
	}

	private static final int MSG_TEXT_RECEIVED_SUCCESS = 1;
	private static final int MSG_PROGRESSBAR_VISIBLE = 2;
	private static final int MSG_PROGRESSBAR_UPDATING = 3;
	private static final int MSG_PROGRESSBAR_GONE = 4;
	private static final int MSG_FILE_RECEIVED_SUCCESS = 5;
	
	Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case MSG_TEXT_RECEIVED_SUCCESS:		//文本消息接收成功
				String[] args = (String[]) msg.obj;
				listMsg.add(new Msg(args[0], args[1], args[2], args[3]));
				adapter.notifyDataSetChanged();
				break;			
			case MSG_PROGRESSBAR_VISIBLE:		//设置进度条可见
				if(pb.getVisibility()==View.GONE){
					pb.setMax(100);
					pb.setProgress(0);
					pb.setVisibility(View.VISIBLE);
				}
				break;
			case MSG_PROGRESSBAR_UPDATING:		//更新进度条
				pb.setProgress(msg.arg1);
				break;
			case MSG_PROGRESSBAR_GONE:		//进度条置为隐藏
				pb.setVisibility(View.GONE);
				break;
			case MSG_FILE_RECEIVED_SUCCESS:		//接收到的文件处理
				final IncomingFileTransfer infiletransfer = request.accept();
				AlertDialog.Builder builder = new AlertDialog.Builder(FormClient.this);
				builder.setTitle("receive file")
						.setCancelable(false)
						.setPositiveButton("Receive",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										try {
											infiletransfer.recieveFile(file);
										} catch (XMPPException e) {
											e.printStackTrace();
										}
										handler.sendEmptyMessage(MSG_PROGRESSBAR_VISIBLE);
										Timer timer = new Timer();
										//开启一个TimerTask 实时计算文件接收的进度
										TimerTask updateProgessBar = new TimerTask() {
											public void run() {
												if ((infiletransfer.getAmountWritten() >= request.getFileSize())
														|| (infiletransfer.getStatus() == FileTransfer.Status.error)
														|| (infiletransfer.getStatus() == FileTransfer.Status.refused)
														|| (infiletransfer.getStatus() == FileTransfer.Status.cancelled)
														|| (infiletransfer.getStatus() == FileTransfer.Status.complete)) {
													cancel();
													handler.sendEmptyMessage(MSG_PROGRESSBAR_GONE);
												} else {
													//文件接收的进度
													long p = infiletransfer.getAmountWritten() * 100L / infiletransfer.getFileSize();													
													android.os.Message message = handler.obtainMessage();
													message.arg1 = Math.round((float) p);
													message.what = MSG_PROGRESSBAR_UPDATING;
													message.sendToTarget();
												}
											}
										};
										timer.scheduleAtFixedRate(updateProgessBar, 10L, 10L);
										dialog.dismiss();
									}
								})
						.setNegativeButton("Reject",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										request.reject();
										dialog.cancel();
									}
								}).show();
				break;
			default:
				break;
			}
		};
	};

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		XmppTool.closeConnection();
		System.exit(0);
	}

	class MyAdapter extends BaseAdapter {

		private Context cxt;
		private LayoutInflater inflater;

		public MyAdapter(FormClient formClient) {
			this.cxt = formClient;
		}

		@Override
		public int getCount() {
			return listMsg.size();
		}

		@Override
		public Object getItem(int position) {
			return listMsg.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			this.inflater = (LayoutInflater) this.cxt.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			if(listMsg.get(position).from.equals("IN")){
				convertView = this.inflater.inflate(R.layout.formclient_chat_in, null);
			}else{
				convertView = this.inflater.inflate(R.layout.formclient_chat_out, null);
			}
			TextView useridView = (TextView) convertView.findViewById(R.id.formclient_row_userid);
			TextView dateView = (TextView) convertView.findViewById(R.id.formclient_row_date);
			TextView msgView = (TextView) convertView.findViewById(R.id.formclient_row_msg);
			useridView.setText(listMsg.get(position).userid);
			dateView.setText(listMsg.get(position).date);
			msgView.setText(listMsg.get(position).msg);
			return convertView;
		}
	}
}