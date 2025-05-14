package com.aurora.bedrocktest;

import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class Crypto {
    public static byte[] encrypt(byte[] data, byte[] key) {
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

    public static byte[] decrypt(byte[] data, byte[] key) {
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

    public static byte[] hmac(byte[] data, byte[] key) {
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

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return keyGen.generateKeyPair();
    }
    public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        X500Name dnName = new X500Name("CN=WebRTC");
        BigInteger certSerialNumber = BigInteger.valueOf(System.currentTimeMillis());
        Date startDate = new Date(System.currentTimeMillis() - 3600 * 1000);
        Date endDate = new Date(System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000));
        Security.insertProviderAt(new BouncyCastleProvider(), 1);

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider("AndroidOpenSSL").build(keyPair.getPrivate());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("AndroidOpenSSL").getCertificate(certBuilder.build(contentSigner));

        cert.verify(keyPair.getPublic());  // 验证证书有效性（可选）
        return cert;
    }

    public static String getFingerprint(X509Certificate cert) throws Exception {
        byte[] encoded = cert.getEncoded();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(encoded);

        StringBuilder fingerprint = new StringBuilder();
        for (byte b : hash) {
            fingerprint.append(String.format("%02X:", b));
        }
        return fingerprint.substring(0, fingerprint.length() - 1); // 移除最后一个冒号
    }

    public static String convertPrivateKeyToPEM(PrivateKey privateKey) throws Exception {
        StringWriter writer = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
        pemWriter.writeObject(privateKey);
        pemWriter.close();
        return writer.toString();
    }
    public static String convertCertificateToPEM(X509Certificate certificate) throws Exception {
        StringWriter writer = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
        pemWriter.writeObject(certificate);
        pemWriter.close();
        return writer.toString();
    }

}
