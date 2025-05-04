package com.aurora.bedrocktest;

import android.util.Log;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {
    public byte[] encrypt(byte[] data, byte[] key) {
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        byte[] encryptedPacket = new byte[0];
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            encryptedPacket = cipher.doFinal(data);

        }catch (Exception e){
            Log.d("Crypto", e.getMessage()!=null?e.getMessage():"null");
        }
        return encryptedPacket;
    }

    public byte[] decrypt(byte[] data, byte[] key) {
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        byte[] decryptedPacket = new byte[0];
        try{
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            decryptedPacket = cipher.doFinal(data);
        }catch (Exception e){
            Log.d("Crypto", e.getMessage()!=null?e.getMessage():"null");
        }
        return decryptedPacket;
    }

    public byte[] hmac(byte[] data, byte[] key) {
        byte[] hash = new byte[0];
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKey secretKey = new SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKey);
            hash = mac.doFinal(data);
        }catch (Exception e){
            Log.d("error", e.getMessage()!=null?e.getMessage():"null");
        }
        return hash;
    }
}
