package com.xmpp.client;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;

import com.xmpp.client.util.XmppTool;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 登陆页面
 * @author chunjiang.shieh
 *
 */
public class FormLogin extends Activity implements OnClickListener {

	private static final String XMPP_RESOURCES = "Android";
	private EditText useridText, pwdText;
	private LinearLayout layout1, layout2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.formlogin);

		this.useridText = (EditText) findViewById(R.id.formlogin_userid);
		this.pwdText = (EditText) findViewById(R.id.formlogin_pwd);

		this.layout1 = (LinearLayout) findViewById(R.id.formlogin_layout1);
		this.layout2 = (LinearLayout) findViewById(R.id.formlogin_layout2);

		Button btsave = (Button) findViewById(R.id.formlogin_btsubmit);
		btsave.setOnClickListener(this);
		Button btcancel = (Button) findViewById(R.id.formlogin_btcancel);
		btcancel.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.formlogin_btsubmit:
			final String USERID = this.useridText.getText().toString();
			final String PWD = this.pwdText.getText().toString();
			new Thread(new Runnable() {				
				@Override
				public void run() {
					handler.sendEmptyMessage(1);
					try {
						XmppTool.getConnection().login(USERID, PWD,XMPP_RESOURCES);
						Log.i("XMPPClient", "Logged in as " + XmppTool.getConnection().getUser());
						// status
						Presence presence = new Presence(Presence.Type.available);
						XmppTool.getConnection().sendPacket(presence);
						Intent intent = new Intent(FormLogin.this, FormClient.class);
						intent.putExtra("USERID", USERID);
						startActivity(intent);
						FormLogin.this.finish();
					} catch (XMPPException e) {
						XmppTool.closeConnection();
						handler.sendEmptyMessage(2);
					}					
				}
			}).start();
			break;
		case R.id.formlogin_btcancel:
			finish();
			break;
		}
	}
	
	private Handler handler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch(msg.what){
			case 1:
				layout1.setVisibility(View.VISIBLE);
				layout2.setVisibility(View.GONE);
				break;
			case 2:
				layout1.setVisibility(View.GONE);
				layout2.setVisibility(View.VISIBLE);
				Toast.makeText(FormLogin.this, "login failure", Toast.LENGTH_SHORT).show();
				break;
			default:
				break;
			}
		};
	};
}