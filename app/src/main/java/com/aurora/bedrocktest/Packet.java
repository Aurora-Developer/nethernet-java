package com.aurora.bedrocktest;

import android.util.Log;

import org.apache.commons.codec.binary.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class Packet {
    private final Crypto crypto = new Crypto();
    public byte[] getKey() {
        long value = 0x00000000DEADBEEFL;
        ByteBuffer bufferLE = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        bufferLE.putLong(value);
        byte[] bytes = bufferLE.array();
        byte[] hash = new byte[0];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hash = digest.digest(bytes);
        }catch (Exception e){
            Log.d("Packet", e.getMessage()!=null?e.getMessage():"null");
        }
        return hash;
    }
    public byte[] buildRequestPacket(long senderId){
        ByteBuffer payload = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
        payload.putShort((short) 0x00);
        payload.putLong(senderId);
        for (int i = 0;i < 8; i++){
            payload.put((byte) 0x00);
        }
        short packetLength = (short) payload.array().length;
        ByteBuffer packetLengthBuffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        packetLengthBuffer.putShort(packetLength);

        ByteBuffer packetBuffer = ByteBuffer.allocate(18 + 2).order(ByteOrder.LITTLE_ENDIAN);
        packetBuffer.put(packetLengthBuffer.array());
        packetBuffer.put(payload.array());
        byte[] packet = packetBuffer.array();


        byte[] encryptedPacket = crypto.encrypt(packet, getKey());

        byte[] hash = crypto.hmac(packet, getKey());

        ByteBuffer finalPacket = ByteBuffer.allocate(encryptedPacket.length + hash.length).order(ByteOrder.LITTLE_ENDIAN);
        finalPacket.put(hash);
        finalPacket.put(encryptedPacket);
        return finalPacket.array();

    }
    public byte[] buildMessagePacket(long senderId, byte[] data){
        ByteBuffer payload = ByteBuffer.allocate(18+data.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.putShort((short) 0x01);
        payload.putLong(senderId);
        payload.put(data);
        short packetLength = (short) payload.array().length;
        ByteBuffer packetLengthBuffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        packetLengthBuffer.putShort(packetLength);
        ByteBuffer packetBuffer = ByteBuffer.allocate(payload.array().length + 2).order(ByteOrder.LITTLE_ENDIAN);
        packetBuffer.put(packetLengthBuffer.array());
        packetBuffer.put(payload.array());
        byte[] packet = packetBuffer.array();

        byte[] encryptedPacket = crypto.encrypt(packet, getKey());
        byte[] hash = crypto.hmac(packet, getKey());

        ByteBuffer finalPacket = ByteBuffer.allocate(encryptedPacket.length + hash.length).order(ByteOrder.LITTLE_ENDIAN);
        finalPacket.put(hash);
        finalPacket.put(encryptedPacket);
        return finalPacket.array();
    }
    public DiscoveryPacket decodeDiscoveryPacket(byte[] data){
        if (data.length < 32){
            return null;
        }else {
            byte[] payload = Arrays.copyOfRange(data, 32, data.length);
            byte[] decryptedPayload = crypto.decrypt(payload, getKey());
            return new DiscoveryPacket(decryptedPayload);
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static class DiscoveryPacket{
        private final short length;
        private final short type;
        private final long senderId;
        private final byte[] data;
        DiscoveryPacket(byte[] data){
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            length = buffer.getShort();
            type = buffer.getShort();
            senderId = buffer.getLong();
            buffer.position(buffer.position() + 8);
            byte[] dataBytes = new byte[buffer.remaining()];
            buffer.get(dataBytes);
            this.data = dataBytes;
        }

        public short getLength(){
            return length;
        }
        public short getType() {
            return type;
        }
        public long getSenderId() {
            return senderId;
        }
        public byte[] getData() {
            return data;
        }
    }
    public static class ResponsePacket{
        private final int version;
        private final String serverName;
        private final String levelName;
        private final int gameType;
        private final int playerNum;
        private final int maxPlayerNum;
        private final int isEditor;
        private final int isHardcore;
        private final int transportLayer;
        ResponsePacket(byte[] data){
            data = Arrays.copyOfRange(data, 4, data.length);
            Log.d("ResponsePacket", new String(data));
            data = hexStringToByteArray(new String(data));
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            // 输出所有字节
            Log.d("ResponsePacket", "Buffer content: " + Arrays.toString(data));
            version = buffer.get() & 0xff;
            int serverNameLength = buffer.get() & 0xff;
            byte[] serverNameBytes = new byte[serverNameLength];
            buffer.get(serverNameBytes);
            serverName = new String(serverNameBytes);
            int levelNameLength = buffer.get() & 0xff;
            byte[] levelNameBytes = new byte[levelNameLength];
            buffer.get(levelNameBytes);
            levelName = new String(levelNameBytes);
            gameType = buffer.getInt();
            playerNum = buffer.getInt();
            maxPlayerNum = buffer.getInt();
            isEditor = buffer.get() & 0xff;
            isHardcore = buffer.get() & 0xff;
            transportLayer = buffer.getInt();
        }

        public int getVersion() {
            return version;
        }
        public String getServerName() {
            return serverName;
        }
        public String getLevelName() {
            return levelName;
        }
        public int getGameType() {
            return gameType;
        }
        public int getPlayerNum() {
            return playerNum;
        }
        public int getMaxPlayerNum() {
            return maxPlayerNum;
        }
        public int getEditor() {
            return isEditor;
        }
        public int getHardcore() {
            return isHardcore;
        }
        public int getTransportLayer() {
            return transportLayer;
        }
        public String string(){
            return "version: " + version +"; serverName: " + serverName + "; levelName: " + levelName + "; gameType: " + gameType + "; playerNum: " + playerNum + "; maxPlayerNum: " + maxPlayerNum +"; isEditor: " + isEditor +"; isHardcore: " + isHardcore + "; transportLayer: " + transportLayer;
        }
    }
    public static class MessagePacket{
        private Long recipientId;
        private String message;

        MessagePacket(byte[] data){
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            recipientId = buffer.getLong();
            int messageLength = buffer.get() & 0xff;
            byte[] messageBytes = new byte[messageLength];
            buffer.get(messageBytes);
            message = new String(messageBytes);
        }
        MessagePacket(Long recipientId, String message){
            this.recipientId = recipientId;
            this.message = message;
        }
        public Long getRecipientId() {
            return recipientId;
        }
        public String getMessage() {
            return message;
        }
        public byte[] pack(){
            ByteBuffer buffer = ByteBuffer.allocate(8+1+message.length());
            buffer.putLong(recipientId);
            buffer.put((byte) message.length());
            buffer.put(message.getBytes());
            return buffer.array();
        }
    }
}