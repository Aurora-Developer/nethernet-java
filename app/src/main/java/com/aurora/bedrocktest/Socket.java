package com.aurora.bedrocktest;
import android.util.Log;

import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Socket {
    private final int port = 17551;
    private final int targetPort = 7551;
    private final String broadcastAddress = "255.255.255.255";
    private DatagramSocket socket;

    Socket(){
        try {
            this.socket = new DatagramSocket(this.port);
            socket.setSoTimeout(3000); // 设置3秒超时
        } catch (Exception e) {
            Log.d("Socket", e.getMessage()!=null?e.getMessage():"null");
        }
    }

    public void broadcast(byte[] data){
        try{
            InetAddress address = InetAddress.getByName(this.broadcastAddress);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, this.targetPort);
            this.socket.send(packet);
        }catch (Exception e){
            Log.d("Socket", e.getMessage()!=null?e.getMessage():"1");
        }
    }

    public void send(byte[] data, String targetAddress){
        try{
            InetAddress address = InetAddress.getByName(targetAddress);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, this.targetPort);
            this.socket.send(packet);
        }catch (Exception e){
            Log.d("Socket", e.getMessage()!=null?e.getMessage():"2");
        }
    }

    public Map<String, Object> receive(){
        byte[] data = new byte[0];
        String targetIp = "";
        try{
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            this.socket.receive(packet);
            targetIp = packet.getAddress()!=null?packet.getAddress().getHostAddress():"";
            data = new byte[packet.getLength()];
            System.arraycopy(buffer, 0, data, 0, packet.getLength());
        }catch (Exception e){
            Log.d("Socket", e.getMessage()!=null?e.getMessage():"3");
        }
        Map<String, Object> callback = new HashMap<>();
        callback.put("ip", targetIp);
        callback.put("data", data);
        return callback;
    }

    public void close(){
        this.socket.close();
    }
}
