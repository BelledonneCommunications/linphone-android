import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class chiffrement {

	static byte[] removeUselessByte(byte[] tab, int wantedSize) {
		if (wantedSize == tab.length) return tab;
		byte[] newTab = new byte[wantedSize];
		for (int i = 1 ; i < tab.length ; i++) {
			newTab[i-1] = tab[i];
		}
		return newTab;
	}

	static void help() {
		System.out.println("Usage chiffrement:\n\tcrypt(0: decrypt, 1: crypt)\n\tpassword\n\tcontent(to crypt or decrypt)\n\touputName\n\t(only when crypt):" +
				"\n\t\tsalt(16 bytes in hexa)\n\t\tiv(32 byte in hexa)");
	}

	public static void main(String[] args) {
		if (args.length >= 4) {
			String encrypted;
			String crypt = args[0];
			String password = args[1];
			String content = args[2];
			String outputName = args[3];
			System.out.println((crypt.compareTo("0") == 0) ? "Decrypt\n" : "Crypt\n" + "Password: " + password + "\nContent: " + content + "\nOutput File: " + outputName);
			File output = new File(outputName);

			if (crypt.compareTo("0") == 0) {
				try {
					byte[] unBased64Data = content.getBytes();
					ByteArrayInputStream inputStream = new ByteArrayInputStream(unBased64Data);

					byte[] salt = new byte[16];
					byte[] iv = new byte[32];
					byte[] contentToDecrypt = new byte[unBased64Data.length - 48];

					inputStream.read(salt);
					inputStream.read(iv);
					inputStream.read(contentToDecrypt);

					String saltString = new String(salt);
					String ivString = new String(iv);
					BigInteger saltHex = new BigInteger(saltString, 16);
					BigInteger ivHex = new BigInteger(ivString, 16);

					byte[] saltByte = removeUselessByte(saltHex.toByteArray(), 8);
					byte[] ivByte = removeUselessByte(ivHex.toByteArray(), 16);

					SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256");
					KeySpec keySpec = new PBEKeySpec(password.toCharArray(), saltByte, 10000, 128);
					SecretKey tmpSecretKey = factory.generateSecret(keySpec);
					SecretKeySpec secretKeySpec = new SecretKeySpec(tmpSecretKey.getEncoded(), "AES");

					Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
					cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(ivByte));

					encrypted = new String(cipher.doFinal(Base64.getDecoder().decode(contentToDecrypt)));
				} catch (Exception ex) {
					System.out.println(ex);
					return;
				}
			} else if (args.length >= 6){
				try {
					String saltArg = args[4];
					String ivArg = args[5];
					BigInteger saltHex = new BigInteger(saltArg, 16);
					BigInteger ivHex = new BigInteger(ivArg, 16);

					byte[] contentToCrypt = content.getBytes(Charset.forName("UTF-8"));

					byte[] salt = removeUselessByte(saltHex.toByteArray(), 8);
					byte[] iv = removeUselessByte(ivHex.toByteArray(), 16);

					SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2withHmacSHA256");
					KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 128);
					SecretKey tmp = factory.generateSecret(spec);
					SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
					IvParameterSpec ivspec = new IvParameterSpec(iv);

					Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
					cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
					byte[] encryptedBytes = cipher.doFinal(contentToCrypt);

					encrypted = new String(saltArg) + new String(ivArg) + new String(Base64.getEncoder().encode(encryptedBytes));

				} catch (Exception ex) {
					System.out.println(ex);
					return;
				}
			} else {
				help();
				return;
			}
			try {
				BufferedWriter writer = Files.newBufferedWriter(output.toPath(), Charset.forName("UTF-8"));
				writer.write(encrypted, 0, encrypted.length());
				writer.close();
			} catch (Exception ex) {
				System.out.println(ex);
			}
		} else {
			help();
		}
	}
}
