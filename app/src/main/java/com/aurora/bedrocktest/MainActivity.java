package com.aurora.bedrocktest;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private Packet packet;
    private WebRTC webRTC;
    private Thread socketThread;
    private Boolean running;
    private long senderId;

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
        byte[] requestPacket = packet.buildRequestPacket(senderId);
        Socket socket = new Socket();
        running = true;
        socketThread = new Thread(() -> {
            while (running) {
                try {
                    socket.broadcast(requestPacket);
                    byte[] receivePacket = socket.receive();
                    Log.d("MainActivity", receivePacket.length + "");
                    Packet.DiscoveryPacket discoveredPacket = packet.decodeDiscoveryPacket(receivePacket);
                    if (discoveredPacket != null) {
                        short type = discoveredPacket.getType();
                        long serverId = discoveredPacket.getSenderId();
                        if (type == 1){
                            Packet.ResponsePacket responsePacket = new Packet.ResponsePacket(discoveredPacket.getData());
                            Log.d("MainActivity", responsePacket.string());
                            Log.d("MainActivity", "开始ICE候选协商");
                            //开始交换ICE候选
                            runOnUiThread(()->{
                                startICE(socket, serverId);
                            });
                            running = false;
                            socketThread.interrupt();
                        }
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e("MainActivity", e.getMessage()!=null?e.getMessage():"Unknown error");
                }
            }
        });
        socketThread.start();
    }
    private void startICE(Socket socket, long serverId){
        Packet.MessagePacket messagePacket = new Packet.MessagePacket(serverId, webRTC.buildCandidate("192.168.1.10", "17551"));
        byte[] finalPacket = packet.buildMessagePacket(senderId, messagePacket.pack());
        running = true;
        while (running){
            try {
                socket.broadcast(finalPacket);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.e("MainActivity", e.getMessage()!=null?e.getMessage():"Unknown error");
            }
        }
    }
}