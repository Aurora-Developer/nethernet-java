package com.aurora.bedrocktest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.webrtc.SessionDescription;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private Packet packet;
    private WebRTC webRTC;
    private Thread discoveryThread;
    private Thread iceThread;
    private Boolean running;
    private long senderId;
    private long serverId;
    private long sessionId;
    private long connectId;
    private String serverIp;
    private boolean inICE = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Random random = new Random();
        senderId = random.nextLong();
        packet = new Packet();
        webRTC = new WebRTC();
        startSocket();
    }

    private void startSocket() {
        byte[] emptyPayload = new byte[0];
        byte[] requestPacket = new Packet.DiscoveryPacket((short) 0x00, senderId, emptyPayload).pack();
        Socket socket = new Socket();
        running = true;
        discoveryThread = new Thread(() -> {
            while (running) {
                try {
                    socket.broadcast(requestPacket);
                    Map<String, Object> receive = socket.receive();
                    byte[] receivePacket = (byte[]) receive.get("data");
                    Log.d("MainActivity", receivePacket.length + "");
                    serverIp = (String) receive.get("ip");
                    Packet.DiscoveryPacket discoveredPacket = packet.decodeDiscoveryPacket(receivePacket);
                    if (discoveredPacket != null) {
                        short type = discoveredPacket.getType();
                        serverId = discoveredPacket.getSenderId();
                        Log.d("MainActivity", "type:" + type + " serverId:" + serverId);
                        if (type == 1){
                            Packet.ResponsePacket responsePacket = new Packet.ResponsePacket(discoveredPacket.getData());
                            Log.d("MainActivity", responsePacket.string());
                            //开始交换ICE候选
                            if (!inICE) {
                                Log.d("MainActivity", "开始ICE候选协商");
                                inICE = true;
                                runOnUiThread(() -> {
                                    startICE(socket);
                                });
                            }
                        }
                        else if (type == 2){
                            Log.d("MainActivity", "收到0x02MessagePoacket");
                        }
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("MainActivity", e.getMessage()!=null?e.getMessage():"Unknown error");
                }
            }
        });
        discoveryThread.start();
    }
    private void startICE(Socket socket){
        try {
            //生成密钥
            KeyPair keyPair = Crypto.generateKeyPair();
            X509Certificate cert = Crypto.generateSelfSignedCertificate(keyPair);
            String fingerprint = Crypto.getFingerprint(cert);
            sessionId = WebRTC.randSessionId();
            connectId = WebRTC.randSessionId();
            SessionDescription session = WebRTC.createSdp(sessionId, fingerprint);
            String sdpData = Packet.connectRequest + " " + connectId + " " + session;
            Packet.MessagePacket messagePacket = new Packet.MessagePacket(serverId, sdpData);
            byte[] messagePacketPayload = messagePacket.pack();
            byte[] finalPacket = new Packet.DiscoveryPacket((short) 0x02, senderId, messagePacketPayload).pack();
            iceThread = new Thread(()->{
                socket.send(finalPacket, serverIp);
            });
            iceThread.start();
        }catch (Exception e){
            Log.e("MainActivity", e.getMessage()!=null?e.getMessage():"Unknown error");
        }
    }
}