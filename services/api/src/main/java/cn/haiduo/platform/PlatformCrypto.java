package cn.haiduo.platform;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
public final class PlatformCrypto {
 private PlatformCrypto(){}
 public static byte[] hmac(String secret,byte[] body){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal(body);}catch(GeneralSecurityException e){throw new IllegalStateException("Signature unavailable",e);}}
 public static boolean webhook(String secret,byte[] body,String signature){try{return MessageDigest.isEqual(hmac(secret,body),Base64.getDecoder().decode(signature));}catch(IllegalArgumentException e){return false;}}
 public static String encrypt(String key,String value){try{byte[] raw=Base64.getDecoder().decode(key);if(raw.length!=32)throw new IllegalArgumentException("Encryption key must be 32 bytes");byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(raw,"AES"),new GCMParameterSpec(128,nonce));byte[] result=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(java.nio.ByteBuffer.allocate(nonce.length+result.length).put(nonce).put(result).array());}catch(GeneralSecurityException e){throw new IllegalStateException("Token encryption failed");}}
 public static String decrypt(String key,String value){try{byte[] data=Base64.getDecoder().decode(value);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(key),"AES"),new GCMParameterSpec(128,Arrays.copyOf(data,12)));return new String(cipher.doFinal(Arrays.copyOfRange(data,12,data.length)),StandardCharsets.UTF_8);}catch(GeneralSecurityException|IllegalArgumentException e){throw new IllegalStateException("Token decryption failed");}}
}
