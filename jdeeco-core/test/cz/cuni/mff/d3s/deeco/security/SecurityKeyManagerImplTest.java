package cz.cuni.mff.d3s.deeco.security;

import static org.junit.Assert.*;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Ondřej Štumpf  
 */
public class SecurityKeyManagerImplTest {

	private SecurityKeyManagerImpl target;
	
	@Before
	public void setUp() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		target = new SecurityKeyManagerImpl();
	}
	
	@Test
	public void getPublicKeyTest1() throws InvalidKeyException, CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException, SecurityException, SignatureException, IllegalStateException {
		assertNotNull(target.getPublicKeyFor("role1", null));
	}
	
	@Test
	public void getPublicKeyTest2() throws InvalidKeyException, CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException, SecurityException, SignatureException, IllegalStateException {
		List<Object> args = new LinkedList<>();
		args.add("x");

		Key key1 = target.getPublicKeyFor("role1", null);
		Key key2 = target.getPublicKeyFor("role1", args);
		
		args.add("y");
		Key key3 = target.getPublicKeyFor("role1", args);
		
		assertNotNull(key2);
		assertNotEquals(key1, key2);
		assertNotEquals(key3, key2);
		
	}
	
	@Test
	public void getPublicKeyTest3() throws InvalidKeyException, CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException, SecurityException, SignatureException, IllegalStateException {
		assertEquals(target.getPublicKeyFor("role1", null), target.getPublicKeyFor("role1", null));
	}
	
	@Test
	public void getPrivateKeyTest1() throws InvalidKeyException, CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException, SecurityException, SignatureException, IllegalStateException {
		assertNotNull(target.getPrivateKeyFor("role1", null));
	}
	
	@Test(expected = Throwable.class)
	public void incompatibilityTest() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, CertificateEncodingException, KeyStoreException, SecurityException, SignatureException, IllegalStateException, IllegalBlockSizeException, BadPaddingException {
		String text = "hello";
		
		List<Object> args = new LinkedList<>();
		args.add("x");
		Cipher decryptCipher = Cipher.getInstance("RSA");
		decryptCipher.init(Cipher.DECRYPT_MODE, target.getPrivateKeyFor("role1", args));
		
		args = new LinkedList<>();
		args.add("y");
		Cipher encryptCipher = Cipher.getInstance("RSA");
		encryptCipher.init(Cipher.ENCRYPT_MODE, target.getPublicKeyFor("role1", args));
		
		byte[] cipherText = encryptCipher.doFinal(text.getBytes());
		byte[] decipheredText = decryptCipher.doFinal(cipherText);
	
		assertNotEquals(text, new String(decipheredText));
	}
	
	@Test
	public void compatibilityTest() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, CertificateEncodingException, KeyStoreException, SecurityException, SignatureException, IllegalStateException, IllegalBlockSizeException, BadPaddingException {
		String text = "hello";
		
		Cipher decryptCipher = Cipher.getInstance("RSA");
		decryptCipher.init(Cipher.DECRYPT_MODE, target.getPrivateKeyFor("role1", null));
		
		Cipher encryptCipher = Cipher.getInstance("RSA");
		encryptCipher.init(Cipher.ENCRYPT_MODE, target.getPublicKeyFor("role1", null));
		
		byte[] cipherText = encryptCipher.doFinal(text.getBytes());
		byte[] decipheredText = decryptCipher.doFinal(cipherText);
		
		assertEquals(text, new String(decipheredText));
	}
}