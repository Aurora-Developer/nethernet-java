package com.aurora.bedrocktest;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private Packet packet;
    private Thread socketThread;
    private Boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        packet = new Packet();
        startSocket();
    }

    private void startSocket() {
        byte[] requestPacket = packet.buildRequestPacket();
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
}