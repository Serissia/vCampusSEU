package com.vcampus.client.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Enumeration;

/**
 * 本地配置加密与解密工具类（采用 AES-GCM-128 与设备特征派生密钥）。
 *
 * @author Serissia
 */
public final class CryptoUtil {

    /**
     * 加解密转换模式
     */
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * 密钥规范算法
     */
    private static final String KEY_ALGORITHM = "AES";

    /**
     * 密钥派生算法
     */
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * GCM 认证标签长度（单位：位）
     */
    private static final int TAG_LENGTH_BIT = 128;

    /**
     * GCM 推荐 IV 偏移量字节长度
     */
    private static final int IV_LENGTH_BYTE = 12;

    /**
     * 加密文件标识头（用于平滑兼容旧版明文 JSON）
     */
    private static final String MAGIC_HEADER = "ENC:";

    /**
     * 密钥派生应用级固定盐值
     */
    private static final byte[] KDF_SALT = "vCampusSEU@2026#SecuritySalt".getBytes(StandardCharsets.UTF_8);

    /**
     * 密钥派生迭代轮数（增大暴力破解计算成本）
     */
    private static final int ITERATION_COUNT = 65536;

    /**
     * 派生密钥长度（单位：位）
     */
    private static final int KEY_LENGTH_BIT = 128;

    /**
     * 内存缓存的设备派生密钥实例
     */
    private static volatile SecretKey cachedSecretKey;

    private CryptoUtil() {
    }

    /**
     * 获取基于当前设备指纹派生的对称密钥（双重检查锁单例缓存）。
     *
     * @return AES 密钥实例
     */
    private static SecretKey getSecretKey() {
        if (cachedSecretKey == null) {
            synchronized (CryptoUtil.class) {
                if (cachedSecretKey == null) {
                    try {
                        String fingerprint = getDeviceFingerprint();
                        KeySpec spec = new PBEKeySpec(
                                fingerprint.toCharArray(),
                                KDF_SALT,
                                ITERATION_COUNT,
                                KEY_LENGTH_BIT
                        );
                        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
                        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
                        cachedSecretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
                    } catch (Exception e) {
                        throw new RuntimeException("初始化设备派生密钥失败", e);
                    }
                }
            }
        }
        return cachedSecretKey;
    }

    /**
     * 采集本机稳定特征构造设备指纹。
     * 组合规则：操作系统用户名 + 操作系统名称 + 首个有效物理网卡 MAC 地址。
     *
     * @return 设备指纹字符串
     */
    private static String getDeviceFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(System.getProperty("user.name", "unknown")).append(";");
        fingerprint.append(System.getProperty("os.name", "unknown")).append(";");

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface netIf = interfaces.nextElement();
                    // 排除回环网卡与虚拟网卡，无论是否联网均保留硬件 MAC 查询
                    if (netIf.isLoopback() || netIf.isVirtual()) {
                        continue;
                    }
                    byte[] mac = netIf.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        for (byte b : mac) {
                            fingerprint.append(String.format("%02X", b));
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // 网络接口获取失败时降级依赖基础系统属性
        }
        return fingerprint.toString();
    }

    /**
     * 对明文字符串进行加密，输出带有防篡改标签的复合密文。
     *
     * @param plainText 原始明文
     * @return ENC:Base64(IV + CipherText)
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return MAGIC_HEADER + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("配置文件加密失败", e);
        }
    }

    /**
     * 解密字符串。若数据未加密（未携带标识前缀），原样返回。
     *
     * @param cipherText 待解密密文
     * @return 还原后的明文字符串
     */
    public static String decrypt(String cipherText) {
        if (!isEncrypted(cipherText)) {
            return cipherText;
        }
        try {
            String rawBase64 = cipherText.substring(MAGIC_HEADER.length());
            byte[] decoded = Base64.getDecoder().decode(rawBase64);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] plainTextBytes = cipher.doFinal(encryptedData);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("配置文件解密失败（可能在异机运行或文件被损坏）", e);
        }
    }

    /**
     * 校验文本是否包含加密标识头。
     *
     * @param text 文本
     * @return 是否已加密
     */
    public static boolean isEncrypted(String text) {
        return text != null && text.startsWith(MAGIC_HEADER);
    }
}